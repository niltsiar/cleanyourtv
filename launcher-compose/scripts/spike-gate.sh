#!/usr/bin/env bash
# Spike gate: builds the release APK, installs it on the TV and prints the gate numbers with PASS/FAIL.
#
# Usage:   scripts/spike-gate.sh
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          PYTHON      python3 binary (default: python3 on PATH)
#          GLASS       refractive (default) or flat: the glass kind to build, so two builds can be compared
#          THEME_DIR   the theme folder (with config.json) to push back after the run; required on a TV below API 34
#          TV_HOST     the TV's address for that push (default: TV_SERIAL without its port)
#
# It refuses to run (exit 2) while dev.niltsiar.tvhome is the TV's Home: switch-home.sh old first, new after.
# It installs dev.niltsiar.tvhome only. It never changes the TV's Home setting or any other package.
#
# The app's data: below API 34 the Macrobenchmark library resets the app's compilation by uninstalling and installing it
# again, which deletes the pushed theme (in the app's external files directory), the Home order, the library choices and
# the last-opened times. No Gradle flag stops that. Below API 34 the script therefore refuses to run (exit 2) unless
# THEME_DIR names the theme folder, and it pushes that folder back with tools/theme.sh when the run ends. The order, the
# library choices and the last-opened times stay lost. From API 34 the compilation is reset in place, and
# android.injected.androidTest.leaveApksInstalledAfterRun=true keeps AGP from uninstalling the app after the connected
# tests, so the data survives.
set -euo pipefail

usage() {
  echo "usage: [GLASS=refractive|flat] TV_SERIAL=<tv ip>:5555 $0" >&2
}

if [ "$#" -ne 0 ]; then
  usage
  exit 2
fi

GLASS="${GLASS:-refractive}"
case "$GLASS" in
  refractive | flat) ;;
  *)
    echo "GLASS must be refractive or flat, not '$GLASS'" >&2
    usage
    exit 2
    ;;
esac

TV_SERIAL="${TV_SERIAL:?set TV_SERIAL to the TV adb serial, such as <tv ip>:5555}"
ADB="${ADB:-adb}"
PYTHON="${PYTHON:-python3}"
PACKAGE="dev.niltsiar.tvhome"
ACTIVITY="$PACKAGE/.MainActivity"

MAX_JANK_PERCENT=5
# Fewer frames than this means the sweep in HomeSweep.kt did not move focus.
MIN_SWEEP_FRAMES=30
MAX_PSS_MB=80
MAX_COLD_START_MS=1500
MAX_APK_BYTES=$((6 * 1024 * 1024))

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE=("$ROOT/gradlew" --console=plain "-Ptvhome.glass=$GLASS" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)

for tool in "$ADB" "$PYTHON"; do
  command -v "$tool" > /dev/null || {
    echo "missing tool: $tool" >&2
    exit 2
  }
done

adb_tv() { "$ADB" -s "$TV_SERIAL" "$@"; }

if ! adb_tv get-state > /dev/null 2>&1; then
  "$ADB" connect "$TV_SERIAL" > /dev/null 2>&1 || true
  adb_tv get-state > /dev/null 2>&1 || {
    echo "TV $TV_SERIAL is not reachable over adb (is it on, with network debugging enabled?)" >&2
    exit 1
  }
fi

# While we are Home, the system relaunches us after the benchmark's force-stop, so coldStartup is not cold.
HOME_ACTIVITY="$(
  adb_tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2> /dev/null |
    tr -d '\r' | tail -n 1
)" || HOME_ACTIVITY=""
case "$HOME_ACTIVITY" in
  "$PACKAGE"/*)
    echo "$PACKAGE is the TV's Home ($HOME_ACTIVITY), so the cold-start benchmark would not start cold." >&2
    echo "Run scripts/switch-home.sh old first, run this gate, then run scripts/switch-home.sh new." >&2
    exit 2
    ;;
esac

# Gradle's connected-test and profile tasks use every attached device unless told which one.
export ANDROID_SERIAL="$TV_SERIAL"
cd "$ROOT"

SDK="$(adb_tv shell getprop ro.build.version.sdk | tr -d '\r')"
case "$SDK" in
  '' | *[!0-9]*)
    echo "could not read the TV's API level (ro.build.version.sdk: '$SDK')" >&2
    exit 1
    ;;
esac

# The jank and overrun rows need frameOverrunMs, which Macrobenchmark records only on API 31+.
if [ "$SDK" -lt 31 ]; then
  echo "the TV runs API $SDK; the jank and overrun rows need frameOverrunMs, recorded only on API 31+" >&2
  exit 2
fi

# Below API 34 Macrobenchmark reinstalls the app to reset its compilation, which deletes the app's data (see the header).
WIPES_DATA=0
THEME_HOST="${TV_HOST:-}"
if [ "$SDK" -lt 34 ]; then
  WIPES_DATA=1
  if [ -z "${THEME_DIR:-}" ] || [ ! -f "${THEME_DIR:-}/config.json" ]; then
    echo "the TV runs API $SDK: the benchmark reinstalls $PACKAGE, which deletes its theme, order, library choices and last-opened data." >&2
    echo "Set THEME_DIR to the theme folder (with config.json) to push back afterwards." >&2
    exit 2
  fi
  THEME_DIR="$(cd "$THEME_DIR" && pwd)"
  if [ -z "$THEME_HOST" ]; then
    case "$TV_SERIAL" in
      *:*) THEME_HOST="${TV_SERIAL%:*}" ;;
      *)
        echo "set TV_HOST to the TV's address: TV_SERIAL '$TV_SERIAL' has no host to push the theme back to" >&2
        exit 2
        ;;
    esac
  fi
fi

# The report's frame limit is one refresh period (1000 / refresh rate), read from the TV. The active
# display mode prints refreshRate= up to API 34 and vsyncRate= (next to peakRefreshRate=) from API 35.
# Each field must follow "{" or ", " so refreshRate= does not match inside peakRefreshRate=. The whole
# dump is read into a variable and parsed without a pipe, so nothing dies of SIGPIPE under pipefail.
display_refresh_hz() {
  local dump line field hz=""
  dump="$(adb_tv shell dumpsys display 2> /dev/null | tr -d '\r')" || true
  if [[ "$dump" == *mActiveSfDisplayMode=* ]]; then
    line="${dump#*mActiveSfDisplayMode=}"
    line="${line%%$'\n'*}"
    line=", ${line#*\{}"
    for field in vsyncRate refreshRate; do
      if [[ "$line" == *", $field="* ]]; then
        hz="${line#*", $field="}"
        hz="${hz%%[!0-9.]*}"
        if awk -v hz="$hz" 'BEGIN { exit !(hz ~ /^[0-9]+(\.[0-9]+)?$/ && hz + 0 > 0) }'; then
          echo "$hz"
          return 0
        fi
      fi
    done
  fi
  echo "could not read the display refresh rate (vsyncRate= or refreshRate=) from mActiveSfDisplayMode in dumpsys display" >&2
  return 1
}

REFRESH_HZ="$(display_refresh_hz)" || exit 1

# The connected tests leave the benchmark build installed, so put the release build back whatever happens, and
# below API 34 the theme the reinstall deleted. APK is empty until the release build exists.
APK=""
finish() {
  [ -n "$APK" ] || return 0
  echo "== Reinstall release APK"
  adb_tv install -r "$APK" > /dev/null || {
    echo "could not reinstall $APK" >&2
    return 0
  }
  if [ "$WIPES_DATA" = 1 ]; then
    echo "== Push the theme back"
    TV_HOST="$THEME_HOST" TV_PACKAGE="$PACKAGE" "$ROOT/../tools/theme.sh" "$THEME_DIR" > /dev/null ||
      echo "could not push the theme back: run TV_HOST=$THEME_HOST TV_PACKAGE=$PACKAGE tools/theme.sh $THEME_DIR" >&2
  fi
}
trap finish EXIT

# Baseline profile collection needs API 33+ (or root); HomeBenchmarks compiles from warmup runs either way.
if [ "$SDK" -ge 33 ]; then
  echo "== Baseline profile"
  "${GRADLE[@]}" ":app:generateReleaseBaselineProfile"
  COMPILATION="baseline profile plus a speed-profile from warmup runs (API $SDK)"
else
  echo "== No baseline profile: API $SDK cannot collect one without root, so the profile comes from warmup runs"
  COMPILATION="speed-profile from warmup runs, no baseline profile (API $SDK)"
fi

# The provider in data/apps lists LEANBACK_LAUNCHER and LAUNCHER activities, one tile per package, minus Home itself.
APP_COUNT="$(
  for category in LEANBACK_LAUNCHER LAUNCHER; do
    adb_tv shell cmd package query-activities --components -a android.intent.action.MAIN \
      -c "android.intent.category.$category"
  done | tr -d '\r' | awk -F/ -v own="$PACKAGE" '
    NF > 1 && $1 != own { seen[$1] = 1 }
    END { n = 0; for (p in seen) n++; print n }
  '
)"

echo "== Build and install release APK"
"${GRADLE[@]}" ":app:assembleRelease"
APK="$(find "$ROOT/app/build/outputs/apk/release" -name '*.apk' | head -n 1)"
[ -n "$APK" ] || {
  echo "no release APK found" >&2
  exit 1
}
APK_BYTES="$(wc -c < "$APK" | tr -d ' ')"
adb_tv install -r "$APK" > /dev/null

echo "== Home idle PSS"
adb_tv shell am force-stop "$PACKAGE"
adb_tv shell am start -W -n "$ACTIVITY" > /dev/null
sleep 10
# The reader takes all of stdin before it matches, so adb never dies of SIGPIPE under pipefail.
PSS_KB="$(adb_tv shell dumpsys meminfo "$PACKAGE" | "$PYTHON" -c '
import re, sys
for line in sys.stdin.read().splitlines():
    m = re.match(r"\s*TOTAL PSS:\s*(\d+)", line) or re.match(r"\s*TOTAL\s+(\d+)", line)
    if m:
        print(m.group(1))
        break
')"
[ -n "$PSS_KB" ] || {
  echo "could not read TOTAL PSS from dumpsys meminfo" >&2
  exit 1
}

echo "== Benchmarks"
RESULTS_DIR="$ROOT/baselineprofile/build/outputs/connected_android_test_additional_output"
rm -rf "$RESULTS_DIR"
"${GRADLE[@]}" ":baselineprofile:connectedBenchmarkReleaseAndroidTest" \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.niltsiar.tvhome.baselineprofile.HomeBenchmarks
JSON="$(find "$RESULTS_DIR" -name '*benchmarkData.json' -exec ls -t {} + 2> /dev/null | head -n 1 || true)"
[ -n "$JSON" ] || {
  echo "no benchmarkData.json under $RESULTS_DIR" >&2
  exit 1
}
KEPT="$ROOT/build/spike-gate/$GLASS-$(date -u +%Y%m%dT%H%M%SZ)/benchmarkData.json"
mkdir -p "$(dirname "$KEPT")"
cp "$JSON" "$KEPT"
echo "results kept at $KEPT"

COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2> /dev/null)" || COMMIT=unknown
if [ -n "$(git -C "$ROOT" status --porcelain -- . 2> /dev/null)" ]; then
  COMMIT="$COMMIT+dirty"
fi

echo
"$PYTHON" "$ROOT/scripts/spike-gate-report.py" "$KEPT" \
  --build "$GLASS @ $COMMIT" \
  --compilation "$COMPILATION" \
  --apps "$APP_COUNT" \
  --apk-bytes "$APK_BYTES" \
  --pss-kb "$PSS_KB" \
  --refresh-hz "$REFRESH_HZ" \
  --max-jank-percent "$MAX_JANK_PERCENT" \
  --min-sweep-frames "$MIN_SWEEP_FRAMES" \
  --max-pss-mb "$MAX_PSS_MB" \
  --max-cold-start-ms "$MAX_COLD_START_MS" \
  --max-apk-bytes "$MAX_APK_BYTES"
