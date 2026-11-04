"""Prints the spike gate table from a Macrobenchmark benchmarkData.json and exits 1 if any row fails.

Called by spike-gate.sh, which passes the device-side numbers and the limits.
"""

import argparse
import json
import sys

parser = argparse.ArgumentParser()
parser.add_argument("json_path")
parser.add_argument("--build", required=True)
parser.add_argument("--compilation", required=True)
parser.add_argument("--apps", required=True)
parser.add_argument("--apk-bytes", type=int, required=True)
parser.add_argument("--pss-kb", type=int, required=True)
parser.add_argument("--refresh-hz", type=float, required=True)
parser.add_argument("--max-jank-percent", type=float, required=True)
parser.add_argument("--min-sweep-frames", type=int, required=True)
parser.add_argument("--max-pss-mb", type=float, required=True)
parser.add_argument("--max-cold-start-ms", type=float, required=True)
parser.add_argument("--max-apk-bytes", type=int, required=True)
args = parser.parse_args()
if not args.refresh_hz > 0:
    parser.error("--refresh-hz must be a positive number")
budget_ms = 1000.0 / args.refresh_hz

with open(args.json_path) as handle:
    benchmarks = json.load(handle)["benchmarks"]


def find(name_part, section, metric_name):
    for bench in benchmarks:
        if name_part in bench["name"] and metric_name in bench.get(section, {}):
            return bench[section][metric_name]
    return None


NO_DATA = "not in benchmark output"


# A frame is janky when its CPU time or its overrun exceeds one refresh period. A frame that only
# slips one refresh behind a full buffer queue has a small positive overrun and is not a visible hitch.
# The two series of a run list the same frames in the same order, so they are paired frame by frame.
def jank_percent(budget_ms):
    """Returns (percent or None, why it is None)."""
    overrun = find("dpadSweep", "sampledMetrics", "frameOverrunMs")
    durations = find("dpadSweep", "sampledMetrics", "frameDurationCpuMs")
    if not overrun or not durations:
        return None, NO_DATA
    if [len(run) for run in overrun["runs"]] != [len(run) for run in durations["runs"]]:
        return None, "frameOverrunMs and frameDurationCpuMs differ in length"
    pairs = [
        (cpu, late)
        for cpu_run, late_run in zip(durations["runs"], overrun["runs"])
        for cpu, late in zip(cpu_run, late_run)
    ]
    janky = sum(1 for cpu, late in pairs if cpu > budget_ms or late > budget_ms)
    return (100.0 * janky / len(pairs), "") if pairs else (None, "no sweep frames")


# A sweep run that drew few frames did not move focus, so it measured nothing.
def fewest_sweep_frames():
    runs = (find("dpadSweep", "metrics", "frameCount") or {}).get("runs")
    return min(runs) if runs else None


mb = 1024 * 1024
jank, jank_note = jank_percent(budget_ms)
rows = [
    ("janky frames", jank, "<=", args.max_jank_percent, "%", jank_note),
    ("p90 frame CPU", (find("dpadSweep", "sampledMetrics", "frameDurationCpuMs") or {}).get("P90"), "<=", budget_ms, " ms", NO_DATA),
    ("p90 overrun", (find("dpadSweep", "sampledMetrics", "frameOverrunMs") or {}).get("P90"), "<=", budget_ms, " ms", NO_DATA),
    ("sweep frames", fewest_sweep_frames(), ">=", args.min_sweep_frames, "", NO_DATA),
    ("Home idle PSS", args.pss_kb / 1024.0, "<=", args.max_pss_mb, " MB", NO_DATA),
    ("cold start", (find("coldStartup", "metrics", "timeToInitialDisplayMs") or {}).get("median"), "<=", args.max_cold_start_ms, " ms", NO_DATA),
    ("APK size", args.apk_bytes / mb, "<=", args.max_apk_bytes / mb, " MB", NO_DATA),
]

print("=== Spike gate ===")
print(f"build          {args.build}")
print(f"frame budget   {budget_ms:.2f} ms ({args.refresh_hz:.1f} Hz)")
failed = False
for label, value, op, limit, unit, note in rows:
    bound = f"{op} {limit:.4g}{unit}"
    if value is None:
        print(f"{label:<14} n/a ({note})   limit {bound}   FAIL")
        failed = True
        continue
    ok = value <= limit if op == "<=" else value >= limit
    failed = failed or not ok
    print(f"{label:<14} {value:8.2f}{unit:<4} limit {bound:<11} {'PASS' if ok else 'FAIL'}")
print(f"compilation    {args.compilation}")
print(f"launcher apps  {args.apps}")
print(f"APK bytes      {args.apk_bytes}")
print(f"results        {args.json_path}")
print("OVERALL        " + ("FAIL" if failed else "PASS"))
sys.exit(1 if failed else 0)
