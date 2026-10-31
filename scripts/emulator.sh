#!/usr/bin/env bash
# Runs the GoogleTV_34 emulator and drives it. Every adb call names the serial,
# because a phone and other emulators may be attached too.
#
#   scripts/emulator.sh start               boot the AVD headless (creates it on first use)
#   scripts/emulator.sh install             build stmaterialDebug and install it
#   scripts/emulator.sh shot <file>         save a screenshot
#   scripts/emulator.sh key <keycode...>    send remote keys, e.g. key 20 20 23
set -euo pipefail

AVD=GoogleTV_34
IMAGE='system-images;android-34;google-tv;arm64-v8a'
PORT=5570 # fixed, so that the serial is known and never clashes with another emulator on 5554
SERIAL="emulator-$PORT"
APK_DIR=smarttubetv/build/outputs/apk/stmaterial/debug

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

die() { echo "emulator.sh: $*" >&2; exit 1; }

find_tool() {
    local tool
    tool="$(ls "$SDK"/cmdline-tools/*/bin/"$1" 2>/dev/null | head -1)"
    [ -n "$tool" ] || die "$1 not found under $SDK/cmdline-tools"
    echo "$tool"
}

is_booted() {
    [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

start() {
    if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
        echo no | "$(find_tool avdmanager)" create avd -n "$AVD" -k "$IMAGE" -d tv_1080p
    fi
    if ! is_booted; then
        nohup "$SDK/emulator/emulator" -avd "$AVD" -port "$PORT" \
            -no-window -no-audio -no-boot-anim -no-snapshot-save >/tmp/$AVD.log 2>&1 &
        for _ in $(seq 1 180); do
            is_booted && break
            sleep 2
        done
        is_booted || die "$AVD did not boot, see /tmp/$AVD.log"
    fi
    echo "$AVD is up as $SERIAL"
}

install() {
    (cd "$ROOT" && ./gradlew :smarttubetv:assembleStmaterialDebug)
    "$ADB" -s "$SERIAL" install -r "$ROOT/$APK_DIR"/*_arm64-v8a.apk
}

shot() {
    [ $# -eq 1 ] || die "usage: shot <file>"
    "$ADB" -s "$SERIAL" exec-out screencap -p >"$1"
}

key() {
    [ $# -ge 1 ] || die "usage: key <keycode...>"
    "$ADB" -s "$SERIAL" shell input keyevent "$@"
}

command="${1:-}"
[ $# -eq 0 ] || shift
case "$command" in
    start) start ;;
    install) install ;;
    shot) shot "$@" ;;
    key) key "$@" ;;
    *) die "usage: $0 start|install|shot <file>|key <keycode...>" ;;
esac
