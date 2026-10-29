#!/usr/bin/env bash
# den-tv installer for Mac and Linux. Puts den-tv and the Fan Hub on an Android TV or Google TV on the same Wi-Fi and
# makes den-tv the home screen. Needs nothing installed: it fetches Google's adb tool and the newest den-tv release.
#
#   bash install.sh                 asks for the TV's IP address
#   bash install.sh 192.168.1.50    installs (or updates) on that TV
#   bash install.sh 192.168.1.50 --undo   puts the TV's own home screen back and removes den-tv
#   --no-helper, --no-screensaver, --keep-debugging   answer those questions up front (otherwise it asks, or with no
#   keyboard to ask on, turns the helper and screensaver on and USB debugging off)
#
# The TV needs USB debugging on first (INSTALL.md, step 1). Google's launcher is switched off only once den-tv has
# started, and put back if Home doesn't end up on den-tv, so the TV is never left without a home screen.
set -euo pipefail

REPO="${DEN_REPO:-knightfox1510/den-tv}"
WORK="${DEN_HOME:-$HOME/.den-tv}"
LAUNCHER=tv.den.launcher
COMPONENT=$LAUNCHER/com.gathin.tvhome.MainActivity
FANHUB=tv.den.fanhub
HELPER=$FANHUB/tv.den.fanhub.Helper
DREAM=$LAUNCHER/com.gathin.tvhome.DenDream
# Home screens that come with the TV: Google TV, Android TV, older Android TV.
STOCK="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher"
# Google TV's setup wizard also claims Home and takes it back unless it is switched off too.
WIZARD=com.google.android.tungsten.setupwraith
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
die() { printf '\n\033[31mStopped: %s\033[0m\n' "$*" >&2; exit 1; }
# Whether there is a keyboard to ask on (not when run by another program); the script may itself come through a pipe.
has_tty() { (exec < /dev/tty) 2> /dev/null; }
# ask "question" y|n: true for yes.
ask() {
  local a=""
  if has_tty; then
    if [ "$2" = y ]; then printf '%s [Y/n] ' "$1" > /dev/tty; else printf '%s [y/N] ' "$1" > /dev/tty; fi
    read -r a < /dev/tty || a=""
  fi
  case "${a:-$2}" in [Yy]*) return 0 ;; *) return 1 ;; esac
}

IP=""; UNDO=0; HELPER_ON=""; DREAM_ON=""; ADB_OFF=""
for arg in "$@"; do
  case "$arg" in
    --undo) UNDO=1 ;;
    --no-helper) HELPER_ON=n ;;
    --no-screensaver) DREAM_ON=n ;;
    --keep-debugging) ADB_OFF=n ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) IP=$arg ;;
  esac
done
if [ -z "$IP" ]; then
  has_tty || die "give the TV's IP address: bash install.sh 192.168.1.50"
  printf "The TV's IP address (Settings, Network & Internet, your Wi-Fi), e.g. 192.168.1.50: " > /dev/tty
  read -r IP < /dev/tty
fi
# A port can follow, for TVs whose Wireless debugging shows one (192.168.1.50:41234); otherwise ADB's own 5555.
[[ "$IP" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]{1,5})?$ ]] || die "'$IP' isn't an IP address like 192.168.1.50."
case "$IP" in *:*) T=$IP; IP=${IP%:*} ;; *) T="$IP:5555" ;; esac
mkdir -p "$WORK"

# ---- adb ------------------------------------------------------------------------------------------------------------

if command -v adb > /dev/null 2>&1; then
  ADB=$(command -v adb)
elif [ -x "$WORK/platform-tools/adb" ]; then
  ADB=$WORK/platform-tools/adb
else
  case "$(uname -s)" in Darwin) os=darwin ;; Linux) os=linux ;; *) die "this script is for Mac and Linux; on Windows use install.ps1." ;; esac
  say "Getting adb, Android's own tool for talking to the TV, from Google (about 10 MB)..."
  curl -fL --progress-bar -o "$WORK/platform-tools.zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip" \
    || die "couldn't download adb. Check the internet connection and run this again."
  if command -v unzip > /dev/null 2>&1; then unzip -qo "$WORK/platform-tools.zip" -d "$WORK"
  else python3 -m zipfile -e "$WORK/platform-tools.zip" "$WORK" && chmod +x "$WORK/platform-tools/adb"; fi
  rm -f "$WORK/platform-tools.zip"
  ADB=$WORK/platform-tools/adb
fi

tv() { "$ADB" -s "$T" "$@"; }
# A command on the TV, its output without Windows-style line ends.
sh_() { "$ADB" -s "$T" shell "$@" 2> /dev/null | tr -d '\r'; }
installed() { sh_ pm list packages "$1" | grep -qx "package:$1"; }
home_now() { sh_ "$HOME_QUERY" | tail -n 1; }

say "Connecting to the TV at $IP..."
"$ADB" connect "$T" > /dev/null 2>&1 || true
echo "Look at the TV now: if it asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose Allow."
state=""
for i in $(seq 1 45); do
  state=$("$ADB" -s "$T" get-state 2> /dev/null || true)
  [ "$state" = device ] && break
  if [ $((i % 5)) = 0 ]; then "$ADB" connect "$T" > /dev/null 2>&1 || true; fi
  sleep 2
done
[ "$state" = device ] || die "the TV at $IP didn't answer. Check that it's on, that USB debugging is on (INSTALL.md, step 1),
that the IP address is right and that this computer is on the same Wi-Fi. Then run this again."
echo "Connected to $(sh_ getprop ro.product.model) (Android $(sh_ getprop ro.build.version.release))."

# ---- undo -----------------------------------------------------------------------------------------------------------

if [ "$UNDO" = 1 ]; then
  say "Putting the TV's own home screen back..."
  for p in $STOCK; do
    if installed "$p"; then
      sh_ pm enable --user 0 "$p" > /dev/null || true
      sh_ "cmd package set-home-activity --user 0 \$($HOME_QUERY $p | tail -n 1)" > /dev/null || true
      break
    fi
  done
  if installed $WIZARD; then sh_ pm enable --user 0 $WIZARD > /dev/null || true; fi
  if [ "$(sh_ settings get secure screensaver_components)" = "$DREAM" ]; then sh_ settings delete secure screensaver_components > /dev/null || true; fi
  list=$(sh_ settings get secure enabled_accessibility_services)
  case "$list" in
    *"$HELPER"*)
      rest=$(printf '%s' "$list" | tr ':' '\n' | grep -vx "$HELPER" | paste -sd: - || true)
      if [ -z "$rest" ]; then sh_ settings delete secure enabled_accessibility_services > /dev/null || true
      else sh_ "settings put secure enabled_accessibility_services '$rest'" > /dev/null || true; fi ;;
  esac
  tv uninstall $FANHUB > /dev/null 2>&1 || true
  tv uninstall $LAUNCHER > /dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME > /dev/null || true
  say "Done: den-tv is removed and the TV's own home screen is back. Turn USB debugging off in Developer options."
  exit 0
fi

# ---- install --------------------------------------------------------------------------------------------------------

sdk=$(sh_ getprop ro.build.version.sdk)
[ "${sdk:-0}" -ge 26 ] || die "this TV runs Android SDK ${sdk:-unknown}; den-tv needs Android 8 (SDK 26) or newer."

get_apk() {
  if [ -n "${DEN_APKS:-}" ]; then cp "$DEN_APKS/$1.apk" "$WORK/$1.apk"; return; fi
  curl -fL --progress-bar -o "$WORK/$1.apk" "https://github.com/$REPO/releases/latest/download/$1.apk" \
    || die "couldn't download $1.apk from https://github.com/$REPO/releases (the repo has to be public, with a release)."
}
say "Downloading the newest den-tv..."
get_apk den-tv-launcher
get_apk den-tv-fanhub

install_apk() {
  local out
  out=$(tv install -r "$WORK/$1.apk" 2>&1 || true)
  case "$out" in
    *Success*) echo "$2 installed." ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      die "this TV has a $2 signed with a different key (an older test build). Run: bash install.sh $IP --undo
then run the install again." ;;
    *) die "$2 didn't install: $(printf '%s' "$out" | tail -n 1)" ;;
  esac
}
# The launcher first: it defines the permission the Fan Hub needs to change the look.
say "Installing on the TV (this takes a minute)..."
install_apk den-tv-launcher "den-tv"
install_apk den-tv-fanhub "Fan Hub"

say "Making den-tv the home screen..."
HOME_PKG=$(home_now | cut -d/ -f1 || true)
# An update replaces the running Home; start a fresh one, as Android's restarted copy can draw wrongly.
sh_ am force-stop $LAUNCHER > /dev/null || true
out=$(sh_ am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $COMPONENT)
if ! printf '%s' "$out" | grep -qF 'Status: ok' || printf '%s' "$out" | grep -qi '^Error'; then
  die "den-tv didn't start, so the TV's own home screen was left as it is. What the TV said:
$out"
fi

DISABLED=""
rollback() {
  for p in $DISABLED; do sh_ pm enable --user 0 "$p" > /dev/null || true; done
  case "$HOME_PKG" in
    ""|android|"$LAUNCHER") ;;
    *) sh_ "cmd package set-home-activity --user 0 \$($HOME_QUERY $HOME_PKG | tail -n 1)" > /dev/null || true ;;
  esac
}
for p in $STOCK $WIZARD; do
  # Only what is installed and still on (pm list -e lists enabled packages).
  if sh_ pm list packages -e "$p" | grep -qx "package:$p"; then
    sh_ pm disable-user --user 0 "$p" > /dev/null || true
    DISABLED="$DISABLED $p"
  fi
done
sh_ cmd package set-home-activity --user 0 $COMPONENT > /dev/null || true
now=$(home_now)
case "$now" in
  "$LAUNCHER"/*) echo "den-tv is the home screen." ;;
  *)
    rollback
    die "Home went to '${now:-nothing}' instead of den-tv, so the TV's own home screen was put back. This TV's maker
may lock its home screen; see Troubleshooting in INSTALL.md." ;;
esac

# Lets Home show the Wi-Fi name.
sh_ pm grant $LAUNCHER android.permission.ACCESS_FINE_LOCATION > /dev/null || true

echo
echo "The den-tv helper lets the phone app press Back and Home, type, open apps and count screen time."
echo "It's an accessibility service that only notes which app is in front; it reads nothing else on screen."
if [ "$HELPER_ON" != n ] && ask "Turn the den-tv helper on?" y; then
  list=$(sh_ settings get secure enabled_accessibility_services)
  case "$list" in
    *"$HELPER"*) new=$list ;;
    null|"") new=$HELPER ;;
    *) new="$list:$HELPER" ;;
  esac
  # Cleared first: after an update Android binds the helper again only when the list changes.
  sh_ settings delete secure enabled_accessibility_services > /dev/null || true
  sh_ "settings put secure enabled_accessibility_services '$new'" > /dev/null || true
  sh_ settings put secure accessibility_enabled 1 > /dev/null || true
  echo "Helper on."
fi

echo
if [ "$DREAM_ON" != n ] && ask "Use den-tv's screensaver (the look with a clock and what's next) instead of the TV's own?" y; then
  sh_ settings put secure screensaver_components $DREAM > /dev/null || true
  echo "Screensaver set."
fi

sh_ input keyevent KEYCODE_HOME > /dev/null || true
say "den-tv is on the TV. On the TV, follow the welcome screen to pair your phone."

echo
echo "USB debugging lets any computer on your Wi-Fi that you allow control the TV, so it's best switched off."
echo "You only need it again to update den-tv or to undo it."
if [ "$ADB_OFF" != n ] && ask "Turn USB debugging off now?" y; then
  sh_ settings put global adb_enabled 0 > /dev/null || true
  "$ADB" disconnect "$T" > /dev/null 2>&1 || true
  echo "USB debugging is off."
else
  echo "Remember to turn it off later: Settings, System, Developer options, USB debugging."
fi
