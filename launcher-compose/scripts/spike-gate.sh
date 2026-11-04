#!/usr/bin/env bash
# Spike gate: builds the release APK, installs it on the TV and prints the DoD3 numbers with PASS/FAIL.
#
# Usage:   scripts/spike-gate.sh
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          PYTHON      python3 binary (default: python3 on PATH)
#
# It installs dev.niltsiar.tvhome only. It never changes the TV's Home setting or any other package.
set -euo pipefail

if [ "$#" -ne 0 ]; then
  echo "usage: $0" >&2
  exit 2
fi

TV_SERIAL="${TV_SERIAL:?set TV_SERIAL to the TV adb serial, such as <tv ip>:5555}"
ADB="${ADB:-adb}"
PYTHON="${PYTHON:-python3}"
PACKAGE="dev.niltsiar.tvhome"
ACTIVITY="$PACKAGE/.MainActivity"

# Thresholds from the plan's DoD3.
MAX_JANK_PERCENT=5
MAX_P90_FRAME_MS=16.7
# A frame within its deadline has overrun <= 0, so the p90 frame must not overrun at all.
MAX_P90_OVERRUN_MS=0
MIN_SWEEP_FRAMES=100
MAX_PSS_MB=80
MAX_COLD_START_MS=1500
MAX_APK_BYTES=$((6 * 1024 * 1024))

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE=("$ROOT/gradlew" --console=plain)

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

# The connected test run uninstalls the app when it ends, so put the release build back whatever happens.
reinstall() {
  echo "== Reinstall release APK"
  adb_tv install -r "$APK" > /dev/null || echo "could not reinstall $APK" >&2
}
trap reinstall EXIT

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
KEPT="$ROOT/build/spike-gate/$(date -u +%Y%m%dT%H%M%SZ)/benchmarkData.json"
mkdir -p "$(dirname "$KEPT")"
cp "$JSON" "$KEPT"
echo "results kept at $KEPT"

echo
"$PYTHON" "$ROOT/scripts/spike-gate-report.py" "$KEPT" \
  --compilation "$COMPILATION" \
  --apps "$APP_COUNT" \
  --apk-bytes "$APK_BYTES" \
  --pss-kb "$PSS_KB" \
  --max-jank-percent "$MAX_JANK_PERCENT" \
  --max-p90-frame-ms "$MAX_P90_FRAME_MS" \
  --max-p90-overrun-ms "$MAX_P90_OVERRUN_MS" \
  --min-sweep-frames "$MIN_SWEEP_FRAMES" \
  --max-pss-mb "$MAX_PSS_MB" \
  --max-cold-start-ms "$MAX_COLD_START_MS" \
  --max-apk-bytes "$MAX_APK_BYTES"
