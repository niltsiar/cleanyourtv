#!/usr/bin/env bash
# Spike gate: builds one presenter flavor, installs it on the TV and prints the DoD3 numbers with PASS/FAIL.
#
# Usage:   scripts/spike-gate.sh molecule|transform
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          PYTHON      python3 binary (default: python3 on PATH)
#
# It installs dev.niltsiar.tvhome only. It never changes the TV's Home setting or any other package.
set -euo pipefail

FLAVOR="${1:-}"
case "$FLAVOR" in
  molecule | transform) ;;
  *)
    echo "usage: $0 molecule|transform" >&2
    exit 2
    ;;
esac

TV_SERIAL="${TV_SERIAL:?set TV_SERIAL to the TV adb serial, such as <tv ip>:5555}"
ADB="${ADB:-adb}"
PYTHON="${PYTHON:-python3}"
PACKAGE="dev.niltsiar.tvhome"
ACTIVITY="$PACKAGE/.MainActivity"

# Thresholds from the plan's DoD3.
MAX_JANK_PERCENT=5
MAX_P90_FRAME_MS=16.7
MAX_PSS_MB=80
MAX_COLD_START_MS=1500
MAX_APK_BYTES=$((6 * 1024 * 1024))

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CAP="$(printf '%s' "${FLAVOR:0:1}" | tr '[:lower:]' '[:upper:]')${FLAVOR:1}"
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

echo "== Baseline profile ($FLAVOR)"
"${GRADLE[@]}" ":app:generate${CAP}ReleaseBaselineProfile"

echo "== Build and install release APK ($FLAVOR)"
"${GRADLE[@]}" ":app:assemble${CAP}Release"
APK="$(find "$ROOT/app/build/outputs/apk/$FLAVOR/release" -name '*.apk' | head -n 1)"
[ -n "$APK" ] || {
  echo "no release APK found for $FLAVOR" >&2
  exit 1
}
APK_BYTES="$(wc -c < "$APK" | tr -d ' ')"
adb_tv install -r "$APK" > /dev/null

echo "== Home idle PSS"
adb_tv shell am force-stop "$PACKAGE"
adb_tv shell am start -W -n "$ACTIVITY" > /dev/null
sleep 10
PSS_KB="$(adb_tv shell dumpsys meminfo "$PACKAGE" | "$PYTHON" -c '
import re, sys
for line in sys.stdin:
    m = re.match(r"\s*TOTAL PSS:\s*(\d+)", line) or re.match(r"\s*TOTAL\s+(\d+)", line)
    if m:
        print(m.group(1))
        break
')"
[ -n "$PSS_KB" ] || {
  echo "could not read TOTAL PSS from dumpsys meminfo" >&2
  exit 1
}

echo "== Benchmarks ($FLAVOR)"
RESULTS_DIR="$ROOT/baselineprofile/build/outputs/connected_android_test_additional_output"
rm -rf "$RESULTS_DIR"
"${GRADLE[@]}" ":baselineprofile:connected${CAP}BenchmarkReleaseAndroidTest" \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.niltsiar.tvhome.baselineprofile.HomeBenchmarks
JSON="$(find "$RESULTS_DIR" -name '*benchmarkData.json' -exec ls -t {} + 2> /dev/null | head -n 1 || true)"
[ -n "$JSON" ] || {
  echo "no benchmarkData.json under $RESULTS_DIR" >&2
  exit 1
}

echo
"$PYTHON" - "$JSON" "$FLAVOR" "$APK_BYTES" "$PSS_KB" "$MAX_JANK_PERCENT" "$MAX_P90_FRAME_MS" \
  "$MAX_PSS_MB" "$MAX_COLD_START_MS" "$MAX_APK_BYTES" << 'PY'
import json
import sys

path, flavor, apk, pss_kb, max_jank, max_p90, max_pss, max_cold, max_apk = sys.argv[1:]
apk, pss_kb = int(apk), int(pss_kb)
max_jank, max_p90, max_pss, max_cold, max_apk = (
    float(max_jank), float(max_p90), float(max_pss), float(max_cold), int(max_apk),
)

with open(path) as handle:
    benchmarks = json.load(handle)["benchmarks"]


def find(name_part, section, metric_name):
    for bench in benchmarks:
        if name_part in bench["name"] and metric_name in bench.get(section, {}):
            return bench[section][metric_name]
    return None


# A frame is janky when it overran its deadline (frameOverrunMs > 0, recorded on API 31+).
def jank_percent():
    overrun = find("dpadSweep", "sampledMetrics", "frameOverrunMs")
    durations = find("dpadSweep", "sampledMetrics", "frameDurationCpuMs")
    if not overrun or not durations:
        return None
    frames = [v for run in durations["runs"] for v in run]
    late = [v for run in overrun["runs"] for v in run if v > 0]
    return 100.0 * len(late) / len(frames) if frames else None


cold = (find("coldStartup", "metrics", "timeToInitialDisplayMs") or {}).get("median")
p90 = (find("dpadSweep", "sampledMetrics", "frameDurationCpuMs") or {}).get("P90")
jank = jank_percent()
pss_mb = pss_kb / 1024.0

rows = [
    ("janky frames", jank, f"{max_jank:g} %", lambda v: v <= max_jank, "%"),
    ("p90 frame", p90, f"{max_p90:g} ms", lambda v: v <= max_p90, " ms"),
    ("Home idle PSS", pss_mb, f"{max_pss:g} MB", lambda v: v <= max_pss, " MB"),
    ("cold start", cold, f"{max_cold:g} ms", lambda v: v <= max_cold, " ms"),
    ("APK size", apk / (1024 * 1024), f"{max_apk / (1024 * 1024):g} MB", lambda v: v <= max_apk / (1024 * 1024), " MB"),
]

print(f"=== Spike gate: {flavor} ===")
failed = False
for label, value, limit, ok, unit in rows:
    if value is None:
        print(f"{label:<14} n/a (not in benchmark output)   limit <= {limit}   FAIL")
        failed = True
        continue
    verdict = "PASS" if ok(value) else "FAIL"
    failed = failed or verdict == "FAIL"
    print(f"{label:<14} {value:8.2f}{unit:<4} limit <= {limit:<8} {verdict}")
print(f"APK bytes      {apk}")
print(f"results        {path}")
print("OVERALL        " + ("FAIL" if failed else "PASS"))
sys.exit(1 if failed else 0)
PY
