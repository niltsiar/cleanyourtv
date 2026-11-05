#!/usr/bin/env bash
# Switch which launcher is the TV's Home: the Compose launcher (new) or the old Java launcher (old).
#
# Usage:   scripts/switch-home.sh new|old
# Env:     TV_SERIAL   adb serial of the TV, such as <tv ip>:5555 (required)
#          ADB         adb binary (default: adb on PATH)
#          OLD_HOME    package of the old launcher (default: com.gathin.tvhome)
#          OLD_INPUT_KEY_SERVICE=1   with `old` and no saved state: also turn the old launcher's InputKeyService back on
#          KEYS=keep         leave enabled_accessibility_services and accessibility_enabled as they are (both directions)
#          SCREENSAVER=keep  leave screensaver_components as it is (both directions)
#          SWITCH_HOME_STATE_DIR   where the saved before-values live (default: ~/.cleanyourtv)
#          BIND_WAIT_SECONDS, FOCUS_WAIT_SECONDS   how long to wait for the key service to bind and for Home to be in
#                                                  front (default: 10 each)
#
# Stages: `KEYS=keep SCREENSAVER=keep scripts/switch-home.sh new` changes only Home. Run it again without the two
# variables to move the key service and the screensaver after Home has been seen working.
#
# Order: new checks first (the app is installed, and it has a theme config.json in its external files directory, which a
# reinstall deletes and without which the key remaps vanish; push one with tools/theme.sh and TV_PACKAGE=dev.niltsiar.tvhome),
# then saves the before-values, sets Home, checks the launcher is in front, and only then moves the key service and the
# screensaver. If our KeyService does not bind within BIND_WAIT_SECONDS, the key settings go back as they were. A failure
# after any change prints the command that undoes it.
#
# Saved state: new writes enabled_accessibility_services, accessibility_enabled, screensaver_components and the Home
# activity to $SWITCH_HOME_STATE_DIR/switch-home-<serial>.state before its first change, unless that file exists (a second
# new keeps the first file). old puts those values back exactly, unset ones included, then deletes the file. With no file,
# old falls back to the rules below and says so.
#
# Without saved state the settings move like this:
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

# A secure setting's value as Settings prints it, "null" for an unset one.
read_raw() { adb_tv shell settings get secure "$1" | tr -d '\r'; }

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
THEME_DIR_ON_TV="/sdcard/Android/data/$NEW_PACKAGE/files"

KEYS="${KEYS:-swap}"
SCREENSAVER="${SCREENSAVER:-swap}"
for _opt in "$KEYS" "$SCREENSAVER"; do
  case "$_opt" in
    swap | keep) ;;
    *)
      echo "KEYS and SCREENSAVER take keep (or nothing), not '$_opt'" >&2
      exit 2
      ;;
  esac
done
BIND_WAIT_SECONDS="${BIND_WAIT_SECONDS:-10}"
FOCUS_WAIT_SECONDS="${FOCUS_WAIT_SECONDS:-10}"

STATE_DIR="${SWITCH_HOME_STATE_DIR:-$HOME/.cleanyourtv}"
STATE_FILE="$STATE_DIR/switch-home-${TV_SERIAL//[^A-Za-z0-9._-]/_}.state"
CHANGED=0
CREATED_STATE=0

UNDO="TV_SERIAL=$TV_SERIAL${OLD_HOME:+ OLD_HOME=$OLD_HOME} $0 $([ "$1" = new ] && echo old || echo new)"
on_exit() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    if [ "$CHANGED" = 1 ]; then
      echo "The switch stopped after changing the TV. To undo it, run: $UNDO" >&2
    elif [ "$CREATED_STATE" = 1 ]; then
      rm -f "$STATE_FILE"
    fi
  fi
}
trap on_exit EXIT

# The remote key service of the launcher that becomes Home. Ours does what both old services did, so new swaps both out.
# PREV_SERVICES and PREV_A11Y keep the raw values so a failed bind can put them back.
switch_key_services() {
  local before want
  PREV_SERVICES="$(read_raw enabled_accessibility_services)"
  PREV_A11Y="$(read_raw accessibility_enabled)"
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

# restore_setting NAME RAW: put a setting back to RAW as read_raw gave it; "null" means unset.
restore_setting() {
  local now
  now="$(read_raw "$1")"
  echo "$1 before: $now"
  if [ "$now" = "$2" ]; then
    echo "$1 after:  unchanged"
    return 0
  fi
  if [ "$2" = null ]; then
    adb_tv shell settings delete secure "$1"
  else
    adb_tv shell settings put secure "$1" "'$2'"
  fi
  echo "$1 after:  $(read_raw "$1")"
}

# The Home activity as pkg/Class, or nothing when the TV has none or shows the chooser.
current_home() {
  local out re='^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'
  out="$(adb_tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2> /dev/null | tr -d '\r' | tail -n 1)" || out=""
  if [[ "$out" =~ $re ]] && [ "${out%%/*}" != android ]; then printf '%s' "$out"; fi
}

save_state() {
  local home services a11y dream tmp
  home="$(current_home)"
  services="$(read_raw enabled_accessibility_services)"
  a11y="$(read_raw accessibility_enabled)"
  dream="$(read_raw screensaver_components)"
  mkdir -p "$STATE_DIR"
  chmod 700 "$STATE_DIR" 2> /dev/null || true
  tmp="$STATE_FILE.tmp.$$"
  printf 'HOME=%s\nSERVICES=%s\nA11Y=%s\nDREAM=%s\n' "$home" "$services" "$a11y" "$dream" > "$tmp"
  mv "$tmp" "$STATE_FILE"
  CREATED_STATE=1
  echo "Saved the before-values to $STATE_FILE"
}

# load_state: set S_HOME, S_SERVICES, S_A11Y, S_DREAM from the state file; fail when it is missing or incomplete.
load_state() {
  local line seen=0
  [ -f "$STATE_FILE" ] || return 1
  while IFS= read -r line; do
    case "$line" in
      HOME=*) S_HOME=${line#HOME=}; seen=$((seen + 1)) ;;
      SERVICES=*) S_SERVICES=${line#SERVICES=}; seen=$((seen + 1)) ;;
      A11Y=*) S_A11Y=${line#A11Y=}; seen=$((seen + 1)) ;;
      DREAM=*) S_DREAM=${line#DREAM=}; seen=$((seen + 1)) ;;
    esac
  done < "$STATE_FILE"
  [ "$seen" = 4 ]
}

# Succeeds when our KeyService has a completed binding, read the way tools/theme_restart.py reads the service dump.
key_service_bound() {
  local line current=""
  while IFS= read -r line; do
    case "$line" in
      *"* ServiceRecord{"*) current="" ; case "$line" in *" u0 $NEW_KEY_SERVICE}"*) current=ours ;; esac ;;
      *"requested=true received=true hasBound=true"*) if [ "$current" = ours ]; then return 0; fi ;;
    esac
  done <<< "$(adb_tv shell dumpsys activity services "$NEW_PACKAGE" 2> /dev/null | tr -d '\r')"
  return 1
}

wait_for_key_service() {
  local waited=0
  while ! key_service_bound; do
    if [ "$waited" -ge "$BIND_WAIT_SECONDS" ]; then return 1; fi
    sleep 1
    waited=$((waited + 1))
  done
}

if ! adb_tv get-state > /dev/null 2>&1; then
  "$ADB" connect "$TV_SERIAL" > /dev/null 2>&1 || true
  adb_tv get-state > /dev/null 2>&1 || {
    echo "TV $TV_SERIAL is not reachable over adb (is it on, with network debugging enabled?)" >&2
    exit 1
  }
fi

# old puts the saved values back when the file is there; without it, or with a file that names our launcher as Home
# (new was never run from a clean state), it falls back to the fixed rules in the header.
USE_STATE=0
if [ "$1" = old ]; then
  if load_state && [ "${S_HOME%%/*}" != "$NEW_PACKAGE" ]; then
    USE_STATE=1
    if [ -n "$S_HOME" ]; then
      ACTIVITY=$S_HOME
      PACKAGE=${S_HOME%%/*}
    fi
    echo "Restoring the values saved in $STATE_FILE"
    if [ -z "$S_HOME" ]; then echo "Note: the earlier Home was not recorded; using $ACTIVITY."; fi
  else
    echo "Note: no usable saved state in $STATE_FILE; switching by the fixed rules, which may not match the earlier values."
  fi
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
  case "$(adb_tv shell "test -f $THEME_DIR_ON_TV/config.json && echo present" 2> /dev/null | tr -d '\r')" in
    *present*) ;;
    *)
      echo "$NEW_PACKAGE has no theme config.json in $THEME_DIR_ON_TV, so its key remaps would be missing (a reinstall deletes it)." >&2
      echo "Push the theme first: TV_HOST=<tv ip> TV_PACKAGE=$NEW_PACKAGE tools/theme.sh <theme folder>, run from the repo root. Nothing was changed." >&2
      exit 1
      ;;
  esac
  if [ -f "$STATE_FILE" ]; then
    echo "Keeping the before-values already saved in $STATE_FILE"
  else
    save_state
  fi
  # Reading the Wi-Fi name needs it and the launcher never asks; a TV that refuses leaves the network button without a name.
  echo "== Grant location (for the Wi-Fi name)"
  adb_tv shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1 || echo "not granted; the network button will not show the Wi-Fi name"
fi

echo "== Set $ACTIVITY as Home"
CHANGED=1
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
IN_FRONT=0
for ((tries = 0; tries <= FOCUS_WAIT_SECONDS; tries++)); do
  DUMP="$(adb_tv shell dumpsys activity activities 2> /dev/null | tr -d '\r')" || DUMP=""
  FOCUSED=""
  while IFS= read -r line; do
    case "$line" in
      *mFocusedApp*) FOCUSED="$line" ;;
    esac
  done <<< "$DUMP"
  case "$FOCUSED" in
    *" $PACKAGE/"*)
      IN_FRONT=1
      break
      ;;
  esac
  if [ "$tries" -lt "$FOCUS_WAIT_SECONDS" ]; then sleep 1; fi
done
if [ "$IN_FRONT" != 1 ]; then
  echo "ERROR: $PACKAGE is not in front after setting it as Home. mFocusedApp:${FOCUSED:- (none found)}" >&2
  echo "The key service and the screensaver were not touched." >&2
  exit 1
fi
echo "Home is $PACKAGE and in front"

echo "== Remote key service"
if [ "$KEYS" = keep ]; then
  echo "KEYS=keep: left as it is"
elif [ "$USE_STATE" = 1 ]; then
  restore_setting enabled_accessibility_services "$S_SERVICES"
  restore_setting accessibility_enabled "$S_A11Y"
else
  switch_key_services "$1"
  if [ "$1" = new ] && ! wait_for_key_service; then
    echo "ERROR: $NEW_KEY_SERVICE is not bound after ${BIND_WAIT_SECONDS}s; putting the key settings back" >&2
    restore_setting enabled_accessibility_services "$PREV_SERVICES"
    restore_setting accessibility_enabled "$PREV_A11Y"
    exit 1
  fi
fi

echo "== Screensaver"
if [ "$SCREENSAVER" = keep ]; then
  echo "SCREENSAVER=keep: left as it is"
elif [ "$USE_STATE" = 1 ]; then
  restore_setting screensaver_components "$S_DREAM"
else
  switch_screensaver "$1"
fi

if [ "$USE_STATE" = 1 ] && [ "$KEYS" != keep ] && [ "$SCREENSAVER" != keep ]; then rm -f "$STATE_FILE"; fi
echo "OK: $PACKAGE is Home and in front"
