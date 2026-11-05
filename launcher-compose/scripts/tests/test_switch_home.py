"""scripts/switch-home.sh against a fake adb: the real script runs, only the transport is simulated.

The fake keeps the TV's Settings values and Home activity in a JSON file and appends every adb call to a log, so a
test can read the order of the calls and what was written. Nothing here touches a TV.

Run from the repository root: python3 -m unittest discover -s launcher-compose/scripts/tests -v
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "switch-home.sh"
NEW = "dev.niltsiar.tvhome"
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
elif args[:1] == ["shell"]:
    cmd = args[1:]
    line = " ".join(cmd)
    if line.startswith("pm path"):
        out("package:/data/app/base.apk")
    elif line.startswith("pm grant"):
        pass
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


if __name__ == "__main__":
    unittest.main()
