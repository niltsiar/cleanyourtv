"""Prints the spike gate table from a Macrobenchmark benchmarkData.json and exits 1 if any row fails.

Called by spike-gate.sh, which passes the device-side numbers and the limits.
"""

import argparse
import json
import sys

parser = argparse.ArgumentParser()
parser.add_argument("json_path")
parser.add_argument("--flavor", required=True)
parser.add_argument("--compilation", required=True)
parser.add_argument("--apps", required=True)
parser.add_argument("--apk-bytes", type=int, required=True)
parser.add_argument("--pss-kb", type=int, required=True)
parser.add_argument("--max-jank-percent", type=float, required=True)
parser.add_argument("--max-p90-frame-ms", type=float, required=True)
parser.add_argument("--max-p90-overrun-ms", type=float, required=True)
parser.add_argument("--min-sweep-frames", type=int, required=True)
parser.add_argument("--max-pss-mb", type=float, required=True)
parser.add_argument("--max-cold-start-ms", type=float, required=True)
parser.add_argument("--max-apk-bytes", type=int, required=True)
args = parser.parse_args()

with open(args.json_path) as handle:
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


# A sweep run that drew few frames did not move focus, so it measured nothing.
def fewest_sweep_frames():
    runs = (find("dpadSweep", "metrics", "frameCount") or {}).get("runs")
    return min(runs) if runs else None


mb = 1024 * 1024
rows = [
    ("janky frames", jank_percent(), "<=", args.max_jank_percent, "%"),
    ("p90 frame CPU", (find("dpadSweep", "sampledMetrics", "frameDurationCpuMs") or {}).get("P90"), "<=", args.max_p90_frame_ms, " ms"),
    ("p90 overrun", (find("dpadSweep", "sampledMetrics", "frameOverrunMs") or {}).get("P90"), "<=", args.max_p90_overrun_ms, " ms"),
    ("sweep frames", fewest_sweep_frames(), ">=", args.min_sweep_frames, ""),
    ("Home idle PSS", args.pss_kb / 1024.0, "<=", args.max_pss_mb, " MB"),
    ("cold start", (find("coldStartup", "metrics", "timeToInitialDisplayMs") or {}).get("median"), "<=", args.max_cold_start_ms, " ms"),
    ("APK size", args.apk_bytes / mb, "<=", args.max_apk_bytes / mb, " MB"),
]

print(f"=== Spike gate: {args.flavor} ===")
failed = False
for label, value, op, limit, unit in rows:
    bound = f"{op} {limit:g}{unit}"
    if value is None:
        print(f"{label:<14} n/a (not in benchmark output)   limit {bound}   FAIL")
        failed = True
        continue
    ok = value <= limit if op == "<=" else value >= limit
    failed = failed or not ok
    print(f"{label:<14} {value:8.2f}{unit:<4} limit {bound:<10} {'PASS' if ok else 'FAIL'}")
print(f"compilation    {args.compilation}")
print(f"launcher apps  {args.apps}")
print(f"APK bytes      {args.apk_bytes}")
print(f"results        {args.json_path}")
print("OVERALL        " + ("FAIL" if failed else "PASS"))
sys.exit(1 if failed else 0)
