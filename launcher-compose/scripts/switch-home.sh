#!/usr/bin/env bash
# Switch which launcher is the TV's Home: the Compose launcher (new) or the old Java launcher (old).
#
# Usage:   scripts/switch-home.sh new|old
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          OLD_HOME    package of the old launcher (default: com.gathin.tvhome)
#          OLD_INPUT_KEY_SERVICE=1   with `old`: also turn the old launcher's InputKeyService back on
#
# It sets the Home activity, starts Home, and checks that the chosen launcher is in front. It also moves the two
# Settings values that name the launcher's remote key service and screensaver, so the remote and the screensaver follow Home:
#
#   enabled_accessibility_services  new: the old HomeKeyService and InputKeyService leave the list and
#                                   dev.niltsiar.tvhome/.service.keys.KeyService joins it. old: ours leaves and
#                                   $OLD_HOME/.HomeKeyService joins (InputKeyService only with OLD_INPUT_KEY_SERVICE=1, as
#                                   the script cannot know it was on). Every other entry stays as it was, in order.
#   accessibility_enabled           set to 1 in both directions.
#   screensaver_components          new: the old ClockDream becomes dev.niltsiar.tvhome/.ClockDream. old: ours becomes the
#                                   old one. Only when the setting names that screensaver now; any other screensaver stays.
#
# Each value is printed before and after. It never enables, disables, uninstalls or force-stops a package. new also grants the
# launcher its location permission, which the Wi-Fi name needs. Both launchers stay installed and enabled, so each switch
# can be undone with the other argument.
set -euo pipefail

usage() {
  echo "usage: TV_SERIAL=<tv ip>:5555 $0 new|old" >&2
}

if [ "$#" -ne 1 ]; then
  usage
  exit 2
fi

NEW_PACKAGE="dev.niltsiar.tvhome"
OLD_PACKAGE="${OLD_HOME:-com.gathin.tvhome}"

case "$1" in
  new) PACKAGE="$NEW_PACKAGE" ;;
  old) PACKAGE="$OLD_PACKAGE" ;;
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

# --- list helpers: pure string functions over Settings lists such as a:b:c ---------------------------------------
# One entry can be written pkg/.Cls or pkg/pkg.Cls (Settings writes the first, older tools the second), so entries are
# compared in the long form. Empty entries (a trailing colon, an empty list) are dropped; every other entry keeps its text.
canon() {
  local entry=$1 pkg cls
  case "$entry" in
    */*)
      pkg=${entry%%/*}
      cls=${entry#*/}
      case "$cls" in .*) cls=$pkg$cls ;; esac
      printf '%s/%s' "$pkg" "$cls"
      ;;
    *) printf '%s' "$entry" ;;
  esac
}

# list_has SEP LIST ENTRY: succeeds when LIST holds ENTRY.
list_has() {
  local sep=$1 rest=$2 want entry
  want=$(canon "$3")
  while [ -n "$rest" ]; do
    entry=${rest%%"$sep"*}
    case "$rest" in *"$sep"*) rest=${rest#*"$sep"} ;; *) rest= ;; esac
    if [ -n "$entry" ] && [ "$(canon "$entry")" = "$want" ]; then return 0; fi
  done
  return 1
}

# list_remove SEP LIST ENTRY...: LIST without those entries.
list_remove() {
  local sep=$1 rest=$2 entry out= drop keep
  shift 2
  while [ -n "$rest" ]; do
    entry=${rest%%"$sep"*}
    case "$rest" in *"$sep"*) rest=${rest#*"$sep"} ;; *) rest= ;; esac
    if [ -z "$entry" ]; then continue; fi
    keep=1
    for drop in "$@"; do
      if [ "$(canon "$entry")" = "$(canon "$drop")" ]; then keep=0; fi
    done
    if [ "$keep" = 1 ]; then out=${out:+$out$sep}$entry; fi
  done
  printf '%s' "$out"
}

# list_add SEP LIST ENTRY: LIST with ENTRY at the end, unless it is there already.
list_add() {
  local sep=$1 list=$2 entry=$3
  if list_has "$sep" "$list" "$entry"; then
    printf '%s' "$list"
  else
    printf '%s' "${list:+$list$sep}$entry"
  fi
}
# --- end list helpers ---------------------------------------------------------------------------------------------

# A secure setting's value; empty when it is unset (Settings prints "null").
read_setting() {
  local value
  value="$(adb_tv shell settings get secure "$1" | tr -d '\r')"
  if [ "$value" = null ]; then value=""; fi
  printf '%s' "$value"
}

# put_setting NAME BEFORE WANT: print BEFORE, write WANT when it differs, print what the TV holds afterwards.
put_setting() {
  echo "$1 before: ${2:-<unset>}"
  if [ "$2" = "$3" ]; then
    echo "$1 after:  unchanged"
    return 0
  fi
  adb_tv shell settings put secure "$1" "'$3'"
  echo "$1 after:  $(read_setting "$1")"
}

NEW_KEY_SERVICE="$NEW_PACKAGE/.service.keys.KeyService"
NEW_DREAM="$NEW_PACKAGE/.ClockDream"
OLD_HOME_KEY="$OLD_PACKAGE/.HomeKeyService"
OLD_INPUT_KEY="$OLD_PACKAGE/.InputKeyService"
OLD_DREAM="$OLD_PACKAGE/.ClockDream"

# The remote key service of the launcher that becomes Home. Ours does what both old services did, so new swaps both out.
switch_key_services() {
  local before want
  before="$(read_setting enabled_accessibility_services)"
  if [ "$1" = new ]; then
    want="$(list_add : "$(list_remove : "$before" "$OLD_HOME_KEY" "$OLD_INPUT_KEY")" "$NEW_KEY_SERVICE")"
  else
    want="$(list_add : "$(list_remove : "$before" "$NEW_KEY_SERVICE")" "$OLD_HOME_KEY")"
    if [ "${OLD_INPUT_KEY_SERVICE:-0}" = 1 ]; then want="$(list_add : "$want" "$OLD_INPUT_KEY")"; fi
  fi
  put_setting enabled_accessibility_services "$before" "$want"
  put_setting accessibility_enabled "$(read_setting accessibility_enabled)" 1
}

# The screensaver follows Home only when it is the launcher's own clock now; a screensaver the owner picked stays.
switch_screensaver() {
  local before want from to
  if [ "$1" = new ]; then
    from=$OLD_DREAM
    to=$NEW_DREAM
  else
    from=$NEW_DREAM
    to=$OLD_DREAM
  fi
  before="$(read_setting screensaver_components)"
  want=$before
  if list_has , "$before" "$from"; then want="$(list_add , "$(list_remove , "$before" "$from")" "$to")"; fi
  put_setting screensaver_components "$before" "$want"
}

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

echo "== Remote key service"
switch_key_services "$1"

echo "== Screensaver"
switch_screensaver "$1"

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
