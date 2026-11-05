"""scripts/switch-home.sh and scripts/spike-gate.sh against a fake adb and a fake gradlew: the real scripts run, only the transport and the build are simulated.

The fake keeps the TV's Settings values and Home activity in a JSON file and appends every adb call to a log, so a
test can read the order of the calls and what was written. Nothing here touches a TV.

Run from the repository root: python3 -m unittest discover -s launcher-compose/scripts/tests -v
"""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "switch-home.sh"
GATE = Path(__file__).resolve().parents[1] / "spike-gate.sh"
NEW = "dev.niltsiar.tvhome"
BENCHMARK = f"{NEW}.benchmark"
THEME_STAGE = "/data/local/tmp/tvhome-benchmark-theme"
THEME_SCRIPT = f"{THEME_STAGE}/push-theme.sh"
NEW_KEY = f"{NEW}/.service.keys.KeyService"
OLD = "com.gathin.tvhome"
OLD_HOME_KEY = f"{OLD}/.HomeKeyService"
OLD_INPUT_KEY = f"{OLD}/.InputKeyService"
SERIAL = "192.168.1.50:5555"

FAKE_ADB = r'''#!/usr/bin/env python3
import json
import os
import sys

path = os.environ["FAKE_ADB_STATE"]
with open(path) as f:
    state = json.load(f)
args = sys.argv[1:]
if args[:1] == ["-s"]:
    args = args[2:]
with open(path + ".log", "a") as f:
    f.write(json.dumps(args) + "\n")


def save():
    with open(path, "w") as f:
        json.dump(state, f)


def out(text):
    sys.stdout.write(text + "\n")


if args[:1] == ["get-state"]:
    out("device")
elif args[:1] == ["connect"]:
    out("connected")
elif args[:1] == ["push"]:
    if not os.path.exists(args[1]):
        sys.stderr.write(f"adb: error: cannot stat '{args[1]}'\n")
        sys.exit(1)
elif args[:1] in (["install"], ["uninstall"]):
    # The fake APKs hold the package they carry, so an install shows which package it replaced.
    package = open(args[-1]).read().strip() if args[0] == "install" else args[-1]
    state.setdefault("changed", []).append(f"{args[0]} {package}")
    save()
elif args[:1] == ["shell"]:
    cmd = args[1:]
    line = " ".join(cmd)
    if line.startswith("pm path"):
        out("package:/data/app/base.apk")
    elif line.startswith("pm clear") or line.startswith("pm uninstall"):
        state.setdefault("changed", []).append(line)
        save()
    elif line == "getprop ro.build.version.sdk":
        out(str(state.get("sdk", 34)))
    elif line == "dumpsys display":
        out("  mActiveSfDisplayMode=DisplayMode{id=1, width=3840, height=2160, vsyncRate=60.000004, peakRefreshRate=60.0}")
    elif line.startswith("cmd package query-activities"):
        out("dev.niltsiar.tvhome/dev.niltsiar.tvhome.MainActivity")
        out("com.other.app/com.other.app.Main")
    elif line.startswith("dumpsys meminfo"):
        out("       TOTAL PSS:    51200            TOTAL RSS:    90000")
    elif line.startswith("pm grant"):
        pass
    elif line.startswith("sh /data/local/tmp/"):
        out(state.get("theme_push_output", "theme pushed"))
    elif line.startswith("test -f"):
        if state["theme_config"]:
            out("present")
    elif cmd[:3] == ["settings", "get", "secure"]:
        out(state["settings"].get(cmd[3], "null"))
    elif cmd[:3] == ["settings", "put", "secure"]:
        state["settings"][cmd[3]] = cmd[4].strip("'")
        save()
    elif cmd[:3] == ["settings", "delete", "secure"]:
        state["settings"].pop(cmd[3], None)
        save()
    elif line.startswith("cmd package resolve-activity"):
        out("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true")
        out(state["home"])
    elif line.startswith("cmd package set-home-activity"):
        state["home"] = cmd[-1]
        save()
    elif line.startswith("am start"):
        out("Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.HOME] }")
    elif line == "dumpsys activity activities":
        front = state["home"] if state["home_in_front"] else "com.other/.Activity"
        out(f"  mFocusedApp=ActivityRecord{{1a2b u0 {front} t5}}")
    elif line.startswith("dumpsys activity services"):
        services = state["settings"].get("enabled_accessibility_services", "")
        out("  * ServiceRecord{9f u0 dev.niltsiar.tvhome/.service.keys.KeyService}")
        if state["key_binds"] and "dev.niltsiar.tvhome/.service.keys.KeyService" in services.split(":"):
            out("      * IntentBindRecord{7c}:")
            out("        requested=true received=true hasBound=true")
'''

FAKE_GRADLEW = r'''#!/usr/bin/env bash
root="$(cd "$(dirname "$0")" && pwd)"
echo "$*" >> "$root/gradle.log"
printf '["gradle", "%s"]\n' "$*" >> "$FAKE_ADB_STATE.log"
for task in "$@"; do
  case "$task" in
    :app:assembleRelease)
      mkdir -p "$root/app/build/outputs/apk/release"
      echo dev.niltsiar.tvhome > "$root/app/build/outputs/apk/release/app-release.apk" ;;
    :app:assembleBenchmarkRelease)
      mkdir -p "$root/app/build/outputs/apk/benchmarkRelease"
      echo dev.niltsiar.tvhome.benchmark > "$root/app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk" ;;
    :baselineprofile:connectedBenchmarkReleaseAndroidTest)
      out="$root/baselineprofile/build/outputs/connected_android_test_additional_output/run"
      mkdir -p "$out"
      cp "$root/benchmarkData.json" "$out/x-benchmarkData.json" ;;
  esac
done
'''

BENCHMARK_DATA = {
    "benchmarks": [
        {"name": "coldStartup", "metrics": {"timeToInitialDisplayMs": {"median": 900.0}}},
        {
            "name": "dpadSweep",
            "metrics": {"frameCount": {"runs": [120, 120]}},
            "sampledMetrics": {
                "frameDurationCpuMs": {"P90": 5.0, "runs": [[3.0, 4.0], [3.0, 4.0]]},
                "frameOverrunMs": {"P90": -2.0, "runs": [[-3.0, -4.0], [-3.0, -4.0]]},
            },
        },
    ]
}

OLD_STATE = {
    "home": f"{OLD}/.MainActivity",
    "settings": {
        "enabled_accessibility_services": f"{OLD_HOME_KEY}:{OLD_INPUT_KEY}",
        "accessibility_enabled": "1",
        "screensaver_components": f"{OLD}/.ClockDream",
    },
}


class SwitchHomeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.adb = root / "adb"
        self.adb.write_text(FAKE_ADB)
        self.adb.chmod(0o755)
        self.state_path = root / "tv.json"
        self.state_dir = root / "saved"
        self.tv(**OLD_STATE)

    def tv(self, **fields):
        """Set the fake TV's state, filling what a field leaves out with a TV that behaves."""
        state = {"theme_config": True, "home_in_front": True, "key_binds": True, "settings": {}, "home": ""}
        if self.state_path.exists():
            state = json.loads(self.state_path.read_text())
        state.update(fields)
        self.state_path.write_text(json.dumps(state))

    def read_tv(self):
        return json.loads(self.state_path.read_text())

    def calls(self):
        log = Path(str(self.state_path) + ".log")
        return [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []

    def writes(self):
        """The settings and Home writes, in order, as 'put NAME', 'delete NAME' or 'home'."""
        found = []
        for call in self.calls():
            if call[1:4] == ["settings", "put", "secure"]:
                found.append(f"put {call[4]}")
            elif call[1:4] == ["settings", "delete", "secure"]:
                found.append(f"delete {call[4]}")
            elif "set-home-activity" in call:
                found.append("home")
        return found

    def run_script(self, mode, **env):
        full = dict(os.environ)
        full.update(
            ADB=str(self.adb),
            TV_SERIAL=SERIAL,
            FAKE_ADB_STATE=str(self.state_path),
            SWITCH_HOME_STATE_DIR=str(self.state_dir),
            BIND_WAIT_SECONDS="0",
            FOCUS_WAIT_SECONDS="0",
        )
        full.update(env)
        return subprocess.run(["bash", str(SCRIPT), mode], env=full, capture_output=True, text=True, timeout=60)

    def run_gate(self):
        full = dict(os.environ, ADB=str(self.adb), TV_SERIAL=SERIAL, FAKE_ADB_STATE=str(self.state_path))
        return subprocess.run(["bash", str(GATE)], env=full, capture_output=True, text=True, timeout=60)

    def test_new_sets_home_before_it_swaps_the_key_service_and_screensaver(self):
        result = self.run_script("new")
        self.assertEqual(result.returncode, 0, result.stderr)
        writes = self.writes()
        self.assertEqual(writes[0], "home")
        self.assertIn("put enabled_accessibility_services", writes)
        self.assertIn("put screensaver_components", writes)
        settings = self.read_tv()["settings"]
        self.assertEqual(settings["enabled_accessibility_services"], NEW_KEY)
        self.assertEqual(settings["screensaver_components"], f"{NEW}/.ClockDream")

    def test_new_leaves_the_key_service_alone_when_home_never_comes_to_the_front(self):
        self.tv(home_in_front=False)
        result = self.run_script("new")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(self.writes(), ["home"])
        self.assertIn(f"{SCRIPT} old", result.stderr)
        self.assertIn(f"TV_SERIAL={SERIAL}", result.stderr)

    def test_new_refuses_without_a_theme_config_and_changes_nothing(self):
        self.tv(theme_config=False)
        result = self.run_script("new")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(self.writes(), [])
        self.assertIn(f"TV_PACKAGE={NEW} tools/theme.sh", result.stderr)
        self.assertFalse(self.state_dir.exists() and any(self.state_dir.iterdir()))

    def test_new_puts_the_key_settings_back_when_the_key_service_never_binds(self):
        self.tv(key_binds=False)
        result = self.run_script("new")
        self.assertEqual(result.returncode, 1)
        settings = self.read_tv()["settings"]
        self.assertEqual(settings["enabled_accessibility_services"], OLD_STATE["settings"]["enabled_accessibility_services"])
        self.assertEqual(settings["accessibility_enabled"], "1")
        self.assertIn("not bound", result.stderr)
        self.assertIn(f"{SCRIPT} old", result.stderr)

    def test_old_restores_the_saved_values_exactly_and_deletes_the_state(self):
        before = {
            "home": f"{OLD}/.MainActivity",
            "settings": {
                "enabled_accessibility_services": f"x.y/.Svc:{OLD_HOME_KEY}",
                "screensaver_components": "com.other/.Dream",
            },
        }
        self.tv(**before)
        self.assertEqual(self.run_script("new").returncode, 0)
        self.assertNotEqual(self.read_tv()["settings"], before["settings"])
        result = self.run_script("old")
        self.assertEqual(result.returncode, 0, result.stderr)
        tv = self.read_tv()
        self.assertEqual(tv["settings"], before["settings"])
        self.assertNotIn("accessibility_enabled", tv["settings"])
        self.assertEqual(tv["home"], before["home"])
        self.assertEqual(list(self.state_dir.iterdir()), [])

    def test_old_without_saved_state_uses_the_fixed_rules_and_says_so(self):
        self.tv(
            home=f"{NEW}/.MainActivity",
            settings={
                "enabled_accessibility_services": f"x.y/.Svc:{NEW_KEY}",
                "accessibility_enabled": "0",
                "screensaver_components": f"{NEW}/.ClockDream",
            },
        )
        result = self.run_script("old")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("no usable saved state", result.stdout)
        tv = self.read_tv()
        self.assertEqual(tv["home"], f"{OLD}/.MainActivity")
        self.assertEqual(tv["settings"]["enabled_accessibility_services"], f"x.y/.Svc:{OLD_HOME_KEY}")
        self.assertEqual(tv["settings"]["accessibility_enabled"], "1")
        self.assertEqual(tv["settings"]["screensaver_components"], f"{OLD}/.ClockDream")

    def test_old_restores_keys_and_screensaver_when_the_saved_home_is_ours(self):
        self.tv(home=f"{NEW}/.MainActivity")
        self.assertEqual(self.run_script("new").returncode, 0)
        result = self.run_script("old")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("no usable saved state", result.stdout)
        self.assertIn("is not an earlier launcher", result.stdout)
        tv = self.read_tv()
        self.assertEqual(tv["settings"], OLD_STATE["settings"])
        self.assertEqual(tv["home"], f"{OLD}/.MainActivity")

    def test_spike_gate_refuses_while_the_key_service_is_enabled_and_installs_nothing(self):
        result = self.run_script("new")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.tv(home=f"{OLD}/.MainActivity")
        Path(str(self.state_path) + ".log").unlink()
        gate = self.run_gate()
        self.assertEqual(gate.returncode, 2, gate.stderr)
        self.assertIn("scripts/switch-home.sh old", gate.stderr)
        self.assertEqual(self.writes(), [])
        self.assertFalse([call for call in self.calls() if "install" in call])

    def test_keys_keep_writes_nothing_to_the_key_settings(self):
        result = self.run_script("new", KEYS="keep")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.writes(), ["home", "put screensaver_components"])
        self.assertEqual(self.read_tv()["settings"]["enabled_accessibility_services"], OLD_STATE["settings"]["enabled_accessibility_services"])

    def test_screensaver_keep_writes_nothing_to_the_screensaver(self):
        result = self.run_script("new", SCREENSAVER="keep")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("put screensaver_components", self.writes())
        self.assertEqual(self.read_tv()["settings"]["screensaver_components"], OLD_STATE["settings"]["screensaver_components"])

    def test_a_staged_new_changes_home_first_and_the_rest_on_the_second_run(self):
        self.assertEqual(self.run_script("new", KEYS="keep", SCREENSAVER="keep").returncode, 0)
        self.assertEqual(self.writes(), ["home"])
        self.assertEqual(self.run_script("new").returncode, 0)
        self.assertEqual(self.read_tv()["settings"]["enabled_accessibility_services"], NEW_KEY)
        self.assertEqual(self.run_script("old").returncode, 0)
        self.assertEqual(self.read_tv()["settings"], OLD_STATE["settings"])

    def test_new_keeps_other_services_and_a_second_new_writes_no_setting(self):
        self.tv(
            settings={
                "enabled_accessibility_services": f"x.y/.Svc:{OLD_HOME_KEY}:z.w/z.w.Other:{OLD_INPUT_KEY}",
                "accessibility_enabled": "1",
                "screensaver_components": "com.other/.Dream",
            },
        )
        self.assertEqual(self.run_script("new").returncode, 0)
        self.assertEqual(self.read_tv()["settings"]["enabled_accessibility_services"], f"x.y/.Svc:z.w/z.w.Other:{NEW_KEY}")
        self.assertEqual(self.read_tv()["settings"]["screensaver_components"], "com.other/.Dream")
        Path(str(self.state_path) + ".log").unlink()
        self.assertEqual(self.run_script("new").returncode, 0)
        self.assertEqual(self.writes(), ["home"])
        self.assertEqual(self.run_script("old").returncode, 0)
        self.assertEqual(
            self.read_tv()["settings"]["enabled_accessibility_services"],
            f"x.y/.Svc:{OLD_HOME_KEY}:z.w/z.w.Other:{OLD_INPUT_KEY}",
        )


class SpikeGateRunTest(unittest.TestCase):
    """A whole gate run: the benchmark package takes every install, and dev.niltsiar.tvhome is never touched."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        base = Path(self.tmp.name)
        self.adb = base / "adb"
        self.adb.write_text(FAKE_ADB)
        self.adb.chmod(0o755)
        self.state_path = base / "tv.json"
        self.root = base / "launcher-compose"
        (self.root / "scripts").mkdir(parents=True)
        for name in ("spike-gate.sh", "spike-gate-report.py"):
            (self.root / "scripts" / name).write_text((GATE.parent / name).read_text())
        gradlew = self.root / "gradlew"
        gradlew.write_text(FAKE_GRADLEW)
        gradlew.chmod(0o755)
        (self.root / "benchmarkData.json").write_text(json.dumps(BENCHMARK_DATA))
        self.theme = base / "theme"
        (self.theme / "wallpapers").mkdir(parents=True)
        for name in ("config.json", "wallpaper.jpg", "wallpapers/dusk.JPG", "wallpapers/captions.txt", "wallpapers/notes.md"):
            (self.theme / name).write_text(name)

    def run_gate(self, sdk, theme_dir="default", **state):
        self.state_path.write_text(
            json.dumps(
                {"sdk": sdk, "home": "com.gathin.tvhome/.MainActivity", "settings": {"enabled_accessibility_services": "null"}, **state}
            )
        )
        env = dict(os.environ, ADB=str(self.adb), TV_SERIAL=SERIAL, FAKE_ADB_STATE=str(self.state_path), IDLE_WAIT_SECONDS="0")
        env.pop("THEME_DIR", None)
        if theme_dir == "default":
            theme_dir = self.theme
        if theme_dir is not None:
            env["THEME_DIR"] = str(theme_dir)
        return subprocess.run(
            ["bash", str(self.root / "scripts" / "spike-gate.sh")], env=env, capture_output=True, text=True, timeout=60
        )

    def connected_test_index(self):
        """Where the Gradle test task sits in the shared adb and Gradle call log."""
        return next(i for i, call in enumerate(self.calls()) if call[0] == "gradle" and ":baselineprofile:connectedBenchmarkReleaseAndroidTest" in call[1])

    def calls(self):
        log = Path(str(self.state_path) + ".log")
        return [json.loads(line) for line in log.read_text().splitlines()]

    def test_the_benchmark_package_takes_every_install_below_and_above_api_34(self):
        for sdk in (31, 34):
            with self.subTest(sdk=sdk):
                Path(str(self.state_path) + ".log").unlink(missing_ok=True)
                result = self.run_gate(sdk)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(json.loads(self.state_path.read_text())["changed"], [f"install {BENCHMARK}"])

    def test_no_adb_call_names_the_real_package(self):
        result = self.run_gate(31)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for call in self.calls():
            for word in call:
                self.assertNotEqual(word, NEW, call)
                self.assertFalse(word.startswith(f"{NEW}/"), call)

    def test_the_idle_start_and_memory_reading_use_the_benchmark_package(self):
        result = self.run_gate(34)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        calls = self.calls()
        self.assertIn(["shell", "am", "force-stop", BENCHMARK], calls)
        self.assertIn(["shell", "am", "start", "-W", "-n", f"{BENCHMARK}/{NEW}.MainActivity"], calls)
        self.assertIn(["shell", "dumpsys", "meminfo", BENCHMARK], calls)

    def test_gradle_runs_the_benchmark_build_and_no_install_task(self):
        result = self.run_gate(34)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        tasks = [word for word in (self.root / "gradle.log").read_text().split() if word.startswith(":")]
        self.assertIn(":baselineprofile:connectedBenchmarkReleaseAndroidTest", tasks)
        self.assertEqual([task for task in tasks if "install" in task.lower()], [])

    def test_below_api_34_the_theme_goes_to_the_benchmark_package_before_the_test_task(self):
        result = self.run_gate(31)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        calls = self.calls()
        installed = next(i for i, call in enumerate(calls) if call[0] == "install")
        pushes = [(i, call[-1]) for i, call in enumerate(calls) if call[0] == "push"]
        ran = calls.index(["shell", "sh", THEME_SCRIPT])
        test_task = self.connected_test_index()
        self.assertEqual(
            sorted(dest for _, dest in pushes),
            sorted(
                f"{THEME_STAGE}/{name}"
                for name in ("config.json", "wallpaper.jpg", "wallpapers/dusk.JPG", "wallpapers/captions.txt", "push-theme.sh")
            ),
        )
        self.assertTrue(all(installed < i < ran < test_task for i, _ in pushes), calls)
        self.assertIn(f"-Pandroid.testInstrumentationRunnerArguments.tvhome.themeScript={THEME_SCRIPT}", calls[test_task][1])
        self.assertEqual(calls[-1], ["shell", "rm", "-rf", THEME_STAGE])

    def test_the_pushed_script_writes_through_the_benchmark_provider_and_names_no_other_package(self):
        self.assertEqual(self.run_gate(31).returncode, 0)
        script = (self.root / "build" / "spike-gate" / "push-theme.sh").read_text()
        self.assertIn(f"uri=content://{BENCHMARK}.theme\n", script)
        self.assertEqual(
            re.findall(r"^put (\S+)$", script, re.MULTILINE),
            ["config.json", "wallpaper.jpg", "wallpapers/captions.txt", "wallpapers/dusk.JPG"],
        )
        self.assertTrue(script.endswith(f"am force-stop {BENCHMARK}\necho theme pushed\n"), script)
        self.assertEqual(re.findall(rf"{re.escape(NEW)}(?!\.benchmark)", script), [])

    def test_below_api_34_without_a_theme_dir_the_gate_refuses_before_building_or_installing(self):
        result = self.run_gate(31, theme_dir=None)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("THEME_DIR", result.stderr)
        self.assertFalse((self.root / "gradle.log").exists())
        self.assertFalse([call for call in self.calls() if "install" in call or "push" in call])

    def test_a_theme_dir_without_config_json_or_with_an_unsafe_name_refuses_before_the_tv_is_asked(self):
        (self.theme / "config.json").unlink()
        self.assertEqual(self.run_gate(31).returncode, 2)
        (self.theme / "config.json").write_text("{}")
        (self.theme / "wallpapers" / "a b.png").write_text("x")
        result = self.run_gate(31)
        self.assertEqual(result.returncode, 2)
        self.assertIn("a b.png", result.stderr)
        self.assertFalse(Path(str(self.state_path) + ".log").exists())

    def test_the_gate_stops_before_the_test_task_when_the_theme_does_not_reach_the_package(self):
        result = self.run_gate(31, theme_push_output="config.json: Error while accessing provider")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("did not reach", result.stderr)
        self.assertNotIn("connectedBenchmarkReleaseAndroidTest", (self.root / "gradle.log").read_text())
        self.assertEqual(self.calls()[-1], ["shell", "rm", "-rf", THEME_STAGE])

    def test_from_api_34_the_theme_is_optional_and_nothing_is_staged_without_it(self):
        result = self.run_gate(34, theme_dir=None)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse([call for call in self.calls() if "push" in call or THEME_SCRIPT in call])
        self.assertNotIn("themeScript", (self.root / "gradle.log").read_text())


if __name__ == "__main__":
    unittest.main()
