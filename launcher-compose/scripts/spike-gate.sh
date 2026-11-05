#!/usr/bin/env bash
# Spike gate: builds the release APK and the benchmark build, installs the benchmark build on the TV and prints the gate
# numbers with PASS/FAIL.
#
# Usage:   scripts/spike-gate.sh
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          PYTHON      python3 binary (default: python3 on PATH)
#          GLASS       refractive (default) or flat: the glass kind to build, so two builds can be compared
#          IDLE_WAIT_SECONDS   how long Home idles before its memory is read (default 10)
#          THEME_DIR   the theme folder (with config.json) to measure with; required below API 34, see below
#
# It refuses to run (exit 2) while dev.niltsiar.tvhome is the TV's Home or its KeyService is in
# enabled_accessibility_services: switch-home.sh old first, new after.
#
# The benchmarks run against dev.niltsiar.tvhome.benchmark, the benchmarkRelease build of the same code (app/build.gradle.kts).
# Its manifest has no Home, launcher, accessibility service, screensaver or input switcher entry, so the TV never offers it as
# Home. This script installs that package only. It never installs, reinstalls, clears or uninstalls dev.niltsiar.tvhome, never
# changes the TV's Home setting and never touches another package. The benchmark package stays installed afterwards.
#
# The app's data: below API 34 the Macrobenchmark library resets the app's compilation by uninstalling and installing it
# again, once per test and before the test's first warmup, which deletes that package's data. Here that package is
# dev.niltsiar.tvhome.benchmark, which nobody uses, so only the theme needs putting back: without it Home shows no dock and
# the sweep cannot run. THEME_DIR names the theme folder. The script stages its files and a push script under
# /data/local/tmp, runs the script once before the idle reading, and passes its path to the tests, which run it again in
# their first setup block, after the reset (HomeSweep.kt, pushTheme). The staged files are removed when the script ends.
# From API 34 the compilation is reset in place and THEME_DIR is optional. The baseline profile step (API 33+) is the one
# exception: it targets dev.niltsiar.tvhome itself, as a profile is keyed by the class names of the app people install, and
# Gradle installs the non-minified build over it. android.injected.androidTest.leaveApksInstalledAfterRun=true keeps AGP from
# uninstalling the app under test after the connected tests.
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
# The benchmarkRelease build type adds this suffix. The class name keeps the real package, so the component spells it out.
BENCHMARK_PACKAGE="$PACKAGE.benchmark"
ACTIVITY="$BENCHMARK_PACKAGE/$PACKAGE.MainActivity"
IDLE_WAIT_SECONDS="${IDLE_WAIT_SECONDS:-10}"
THEME_DIR="${THEME_DIR:-}"
THEME_STAGE="/data/local/tmp/tvhome-benchmark-theme"
THEME_SCRIPT="$THEME_STAGE/push-theme.sh"

MAX_JANK_PERCENT=5
# Fewer frames than this means the sweep in HomeSweep.kt did not move focus.
MIN_SWEEP_FRAMES=30
MAX_PSS_MB=80
MAX_COLD_START_MS=1500
MAX_APK_BYTES=$((6 * 1024 * 1024))

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE=("$ROOT/gradlew" --console=plain "-Ptvhome.glass=$GLASS" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)

# The files tools/theme.sh pushes, one path per line. The names go into a shell script, so they stay plain.
theme_files() {
  local file name
  echo config.json
  if [ -f "$THEME_DIR/wallpaper.jpg" ]; then echo wallpaper.jpg; fi
  for file in "$THEME_DIR"/wallpapers/*; do
    [ -f "$file" ] || continue
    name="${file##*/}"
    case "$name" in
      captions.txt | *.[jJ][pP][gG] | *.[jJ][pP][eE][gG] | *.[pP][nN][gG] | *.[wW][eE][bB][pP]) ;;
      *) continue ;;
    esac
    if [[ ! "$name" =~ ^[A-Za-z0-9_.-]+$ || "$name" == *..* ]]; then
      echo "unsupported wallpaper filename in THEME_DIR: $name (letters, digits, '.', '_' and '-' only, no '..')" >&2
      return 1
    fi
    echo "wallpapers/$name"
  done
}

THEME_NAMES=""
if [ -n "$THEME_DIR" ]; then
  [ -f "$THEME_DIR/config.json" ] || {
    echo "THEME_DIR '$THEME_DIR' has no config.json" >&2
    exit 2
  }
  # An absolute path, as the script changes directory below.
  THEME_DIR="$(cd "$THEME_DIR" && pwd)"
  THEME_NAMES="$(theme_files)" || exit 2
fi

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

# A precaution: the gate installs only the benchmark package, but a reinstall of dev.niltsiar.tvhome would drop our key
# service from the enabled list, which strands the remote on a TV that still names it.
SERVICES="$(adb_tv shell settings get secure enabled_accessibility_services 2> /dev/null | tr -d '\r')" || SERVICES=""
case ":$SERVICES:" in
  *":$PACKAGE/.service.keys.KeyService:"* | *":$PACKAGE/$PACKAGE.service.keys.KeyService:"*)
    echo "$PACKAGE/.service.keys.KeyService is in enabled_accessibility_services. The gate does not reinstall $PACKAGE, but" \
      "a reinstall would drop the entry, so it runs only while the service is off." >&2
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

# Below API 34 the benchmark's reset deletes the benchmark package's data, and its default config has no dock (see the header).
if [ "$SDK" -lt 34 ] && [ -z "$THEME_DIR" ]; then
  echo "the TV runs API $SDK: Macrobenchmark reinstalls $BENCHMARK_PACKAGE, which deletes its theme, and without a theme Home has no dock for the sweep." >&2
  echo "Set THEME_DIR to the theme folder (with config.json) to measure with." >&2
  exit 2
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

echo "== Build the release APK and the benchmark build, install the benchmark build"
"${GRADLE[@]}" ":app:assembleRelease" ":app:assembleBenchmarkRelease"
APK="$(find "$ROOT/app/build/outputs/apk/release" -name '*.apk' | head -n 1)"
[ -n "$APK" ] || {
  echo "no release APK found" >&2
  exit 1
}
BENCHMARK_APK="$(find "$ROOT/app/build/outputs/apk/benchmarkRelease" -name '*.apk' | head -n 1)"
[ -n "$BENCHMARK_APK" ] || {
  echo "no benchmark APK found" >&2
  exit 1
}
# The size gate is the APK people install; the benchmark build differs from it only in its manifest.
APK_BYTES="$(wc -c < "$APK" | tr -d ' ')"
adb_tv install -r "$BENCHMARK_APK" > /dev/null

# The script is generated here and run on the TV by the shell user: Macrobenchmark runs it again in each test's first setup
# block, and the UiAutomation shell command it goes through takes no redirects. `content` exits 0 when the provider throws,
# so, as in tools/theme.sh, its output decides. The deletes clear what an earlier theme left in the benchmark package.
if [ -n "$THEME_DIR" ]; then
  echo "== Push the theme to $BENCHMARK_PACKAGE"
  push_script="$ROOT/build/spike-gate/push-theme.sh"
  mkdir -p "$(dirname "$push_script")"
  {
    cat << EOF
cd $THEME_STAGE || exit 1
uri=content://$BENCHMARK_PACKAGE.theme
put() {
  out=\$(content write --uri "\$uri/\$1" < "\$1" 2>&1) || { echo "\$1: \$out"; exit 1; }
  case "\$out" in *Unsupported*|*"Error while accessing provider"*) echo "\$1: \$out"; exit 1 ;; esac
}
for gone in wallpaper.jpg wallpapers/captions.txt wallpapers; do content delete --uri "\$uri/\$gone" > /dev/null 2>&1; done
EOF
    for name in $THEME_NAMES; do echo "put $name"; done
    echo "am force-stop $BENCHMARK_PACKAGE"
    echo "echo theme pushed"
  } > "$push_script"
  trap 'adb_tv shell rm -rf "$THEME_STAGE" > /dev/null 2>&1 || true' EXIT
  adb_tv shell "rm -rf $THEME_STAGE && mkdir -p $THEME_STAGE/wallpapers"
  for name in $THEME_NAMES; do
    adb_tv push "$THEME_DIR/$name" "$THEME_STAGE/$name" > /dev/null
  done
  adb_tv push "$push_script" "$THEME_SCRIPT" > /dev/null
  PUSHED="$(adb_tv shell sh "$THEME_SCRIPT" | tr -d '\r')"
  case "$PUSHED" in
    *"theme pushed") ;;
    *)
      echo "the theme did not reach $BENCHMARK_PACKAGE: $PUSHED" >&2
      exit 1
      ;;
  esac
fi

echo "== Home idle PSS"
adb_tv shell am force-stop "$BENCHMARK_PACKAGE"
adb_tv shell am start -W -n "$ACTIVITY" > /dev/null
sleep "$IDLE_WAIT_SECONDS"
# The reader takes all of stdin before it matches, so adb never dies of SIGPIPE under pipefail.
PSS_KB="$(adb_tv shell dumpsys meminfo "$BENCHMARK_PACKAGE" | "$PYTHON" -c '
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
BENCHMARK_ARGS=(-Pandroid.testInstrumentationRunnerArguments.class=dev.niltsiar.tvhome.baselineprofile.HomeBenchmarks)
if [ -n "$THEME_DIR" ]; then
  BENCHMARK_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.tvhome.themeScript=$THEME_SCRIPT")
fi
"${GRADLE[@]}" ":baselineprofile:connectedBenchmarkReleaseAndroidTest" "${BENCHMARK_ARGS[@]}"
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
