#!/usr/bin/env bash
# Switch which launcher is the TV's Home: the Compose launcher (new) or the old Java launcher (old).
#
# Usage:   scripts/switch-home.sh new|old
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          OLD_HOME    package of the old launcher (default: com.gathin.tvhome)
#
# It sets the Home activity, starts Home, and checks that the chosen launcher is in front. It never
# enables, disables, uninstalls or force-stops a package, and it never changes a setting (new also grants the
# launcher its location permission, which the Wi-Fi name needs). Both launchers
# stay installed and enabled, so each switch can be undone with the other argument.
set -euo pipefail

usage() {
  echo "usage: TV_SERIAL=<tv ip>:5555 $0 new|old" >&2
}

if [ "$#" -ne 1 ]; then
  usage
  exit 2
fi

case "$1" in
  new) PACKAGE="dev.niltsiar.tvhome" ;;
  old) PACKAGE="${OLD_HOME:-com.gathin.tvhome}" ;;
  *)
    usage
    exit 2
    ;;
esac

TV_SERIAL="${TV_SERIAL:?set TV_SERIAL to the TV adb serial, such as <tv ip>:5555}"
ADB="${ADB:-adb}"
ACTIVITY="$PACKAGE/.MainActivity"

command -v "$ADB" > /dev/null || {
  echo "missing tool: $ADB" >&2
  exit 2
}

adb_tv() { "$ADB" -s "$TV_SERIAL" "$@"; }

if ! adb_tv get-state > /dev/null 2>&1; then
  "$ADB" connect "$TV_SERIAL" > /dev/null 2>&1 || true
  adb_tv get-state > /dev/null 2>&1 || {
    echo "TV $TV_SERIAL is not reachable over adb (is it on, with network debugging enabled?)" >&2
    exit 1
  }
fi

PM_PATH="$(adb_tv shell pm path "$PACKAGE" 2> /dev/null | tr -d '\r')" || PM_PATH=""
case "$PM_PATH" in
  package:*) ;;
  *)
    echo "$PACKAGE is not installed on $TV_SERIAL; nothing was changed" >&2
    exit 1
    ;;
esac

if [ "$1" = new ]; then
  # Reading the Wi-Fi name needs it and the launcher never asks; a TV that refuses leaves the network button without a name.
  echo "== Grant location (for the Wi-Fi name)"
  adb_tv shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1 || echo "not granted; the network button will not show the Wi-Fi name"
fi

echo "== Set $ACTIVITY as Home"
adb_tv shell cmd package set-home-activity --user 0 "$ACTIVITY"

echo "== Start Home"
START_OUT="$(adb_tv shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')"
echo "$START_OUT"
case "$START_OUT" in
  *Error*)
    echo "ERROR: Home did not start (see above)" >&2
    exit 1
    ;;
esac

# Home starts asynchronously, so look for a few seconds before giving up.
FOCUSED=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  DUMP="$(adb_tv shell dumpsys activity activities 2> /dev/null | tr -d '\r')" || DUMP=""
  FOCUSED=""
  while IFS= read -r line; do
    case "$line" in
      *mFocusedApp*) FOCUSED="$line" ;;
    esac
  done <<< "$DUMP"
  case "$FOCUSED" in
    *" $PACKAGE/"*)
      echo "OK: $PACKAGE is Home and in front"
      exit 0
      ;;
  esac
  sleep 1
done

echo "ERROR: $PACKAGE is not in front after setting it as Home. mFocusedApp:${FOCUSED:- (none found)}" >&2
echo "Run '$0 $([ "$1" = new ] && echo old || echo new)' to switch back." >&2
exit 1
