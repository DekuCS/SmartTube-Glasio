#!/usr/bin/env bash
# Feed scrolling on a real device: cold start, then D-pad moves through a feed, read from `dumpsys gfxinfo`.
#
#   scripts/perf.sh <serial> [run count]
#
# Environment:
#   PKG    package to measure, default the Material app. org.smarttube.stable is the old-app baseline.
#   SPLASH launch component, default the shared SplashActivity
#   VIEW   a youtube.com link to start from instead of the launcher. A channel has rows with content even
#          signed out, and the same cards in the old and the new app. Default: the channel in CHANNEL below.
#          Set to "" to measure the home screen (needs an account for content).
#   ENTER  keys that bring focus to a feed with content, before the measured part, default none
#   INPUT  "uinput" (default): one long-lived process injects all keys, nothing is spawned per key.
#          "input": one `adb shell input keyevent` per key, as docs/perf.md did. On a weak device
#          each one starts a Java process, and that shows in the numbers.
#   SETTLE seconds to let the feed load after a cold start, default 25
#   STEP   ms between keys, default 300
#   SHOT   a local directory: saves a screenshot after every run, to see that the moves reached a feed
#   MODE   "panel": instead of the feed, the first and second open of the player panel and of the Quality dialog
#
# Refuses to run while something is playing. Touches nothing but the focus of the measured app, and ends
# on the launcher.
set -euo pipefail

[ $# -ge 1 ] || { sed -n 2,20p "$0"; exit 2; }
SERIAL=$1
RUNS=${2:-3}
PKG=${PKG:-io.github.ivanosh.smarttubematerial}
SPLASH=${SPLASH:-com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity}
CHANNEL=https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA
VIEW=${VIEW-$CHANNEL}
ENTER=${ENTER:-}
INPUT=${INPUT:-uinput}
SETTLE=${SETTLE:-25}
STEP=${STEP:-300}
SHOT=${SHOT:-}

# right x10, down, right x5, down, up, up: rows move, new cards come in, thumbnails load behind them.
MOVES="right right right right right right right right right right down right right right right right down up up"

adb_() { adb -s "$SERIAL" "$@"; }

keycode() { # Android key code
    case $1 in up) echo 19 ;; down) echo 20 ;; left) echo 21 ;; right) echo 22 ;; *) echo "$1" ;; esac
}

scancode() { # Linux key code, for uinput
    case $1 in up) echo 103 ;; left) echo 105 ;; right) echo 106 ;; down) echo 108 ;; *) echo "bad key $1" >&2; exit 2 ;; esac
}

refuse_if_playing() {
    if adb_ shell dumpsys media_session | grep -q 'state=PLAYING'; then
        echo "perf.sh: something is playing on $SERIAL, not interrupting it" >&2
        exit 3
    fi
}

# The whole sequence, for `uinput -`: register a D-pad, wait, then all keys on a schedule.
# The register delay is long enough for the gfxinfo reset that follows it.
uinput_script() {
    echo '{"id":1,"command":"register","name":"perf-dpad","vid":24,"pid":1,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[103,105,106,108]}]}'
    echo '{"id":1,"command":"delay","duration":3000}'
    local key code
    for key in $MOVES; do
        code=$(scancode "$key")
        echo "{\"id\":1,\"command\":\"inject\",\"events\":[1,$code,1,0,0,0]}"
        echo '{"id":1,"command":"delay","duration":40}'
        echo "{\"id\":1,\"command\":\"inject\",\"events\":[1,$code,0,0,0,0]}"
        echo "{\"id\":1,\"command\":\"delay\",\"duration\":$((STEP - 40))}"
    done
    echo '{"id":1,"command":"delay","duration":1500}'
}

measured_moves() {
    case $INPUT in
    uinput)
        local script pid
        script=$(mktemp)
        uinput_script >"$script"
        adb_ shell uinput - <"$script" >/dev/null &
        pid=$!
        sleep 2
        adb_ shell dumpsys gfxinfo "$PKG" reset >/dev/null
        wait "$pid"
        rm -f "$script"
        ;;
    input)
        adb_ shell dumpsys gfxinfo "$PKG" reset >/dev/null
        local key
        for key in $MOVES; do
            adb_ shell input keyevent "$(keycode "$key")"
            sleep "$(awk "BEGIN{print $STEP/1000}")"
        done
        sleep 1.5
        ;;
    *) echo "perf.sh: INPUT is uinput or input" >&2; exit 2 ;;
    esac
}

# One line from a gfxinfo dump.
read_gfx() {
    awk '
        /^Total frames rendered:/ && !t { t = $4 }
        /^Janky frames:/ && !j && !/legacy/ { j = $3; p = $4; gsub(/[()]/, "", p) }
        /^50th percentile:/ && !p50 { p50 = $3 }
        /^90th percentile:/ && !p90 { p90 = $3 }
        /^99th percentile:/ && !p99 { p99 = $3 }
        /^90th gpu percentile:/ && !g { g = $4 }
        /^Number Missed Vsync:/ && !v { v = $4; }
        /^Number Slow UI thread:/ && !u { u = $5; }
        /^Number Slow bitmap uploads:/ && !b { b = $5; }
        /^Number Slow issue draw commands:/ && !d { d = $6; }
        /^Number Frame deadline missed:/ && !m { m = $5; }
        END {
            printf "frames=%s janky=%s(%s) p50=%s p90=%s p99=%s gpu90=%s slowUI=%s slowBitmap=%s slowDraw=%s deadline=%s\n",
                t, j, p, p50, p90, p99, g, u, b, d, m
        }' | tr -d '\r'
}

summary() { # median of the percent and percentile columns over all runs
    awk '
        function med(a, n,    i, j, x) {
            for (i = 2; i <= n; i++) { x = a[i]; for (j = i - 1; j > 0 && a[j] > x; j--) a[j + 1] = a[j]; a[j + 1] = x }
            return a[int((n + 1) / 2)]
        }
        {
            n++
            for (i = 1; i <= NF; i++) {
                split($i, kv, "=")
                v = kv[2]
                if (kv[1] == "janky") { sub(/.*\(/, "", v); sub(/[%)].*/, "", v) }
                sub(/ms$/, "", v)
                vals[kv[1], n] = v + 0
            }
        }
        END {
            split("frames janky p50 p90 p99 gpu90 slowUI slowBitmap slowDraw deadline", names, " ")
            line = "median:"
            for (k = 1; k <= 10; k++) {
                for (i = 1; i <= n; i++) a[i] = vals[names[k], i]
                line = line " " names[k] "=" med(a, n)
            }
            print line
        }'
}

# --- First player panel and first dialog ---------------------------------------------------------
# Opens one video, then with one injected D-pad: panel, panel again, Quality dialog, dialog again.
# The time of a key is the kernel's (getevent), the time of a frame is FrameCompleted in framestats:
# the same clock, after an offset if the kernel stamps in real time. Nothing is picked in the dialog, and the player is
# closed at the end.
PANEL_VIDEO=${PANEL_VIDEO:?set PANEL_VIDEO to a video URL; only on a device with no account signed in}
PANEL_WAIT=${PANEL_WAIT:-60}

# In order, key:seconds to the next: panel, close, panel, close, panel, right, right, OK (Quality), close, then the
# panel may have hidden itself, so down and left to the known first button, right, right, OK, close, close, exit.
panel_uinput_script() {
    echo '{"id":1,"command":"register","name":"perf-dpad","vid":24,"pid":1,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[28,103,105,106,108,158,353]}]}'
    echo '{"id":1,"command":"delay","duration":3000}'
    local step
    for step in 108:4 158:3 108:4 158:3 108:2 106:1 106:1 353:6 158:1 108:1 105:1 105:1 106:1 106:1 353:6 158:2 158:2 158:2; do
        echo "{\"id\":1,\"command\":\"inject\",\"events\":[1,${step%:*},1,0,0,0]}"
        echo '{"id":1,"command":"delay","duration":40}'
        echo "{\"id\":1,\"command\":\"inject\",\"events\":[1,${step%:*},0,0,0,0]}"
        echo "{\"id\":1,\"command\":\"delay\",\"duration\":$((${step#*:} * 1000 - 40))}"
    done
}

# framestats on stdin; $1 is the key's time in s, $2 limits to windows whose title contains it.
# Within 4.5 s of the key: "first" is the first frame, "open" the end of the slowest one, which is the frame
# that builds the panel or the dialog (a spinner or a progress tick draws cheap frames around it).
frames_after() {
    awk -v t0="$1" -v only="${2:-}" '
        /Window: / { win = $0; next }
        /^---PROFILEDATA---/ { inside = !inside; col = 0; next }
        inside && /^Flags,/ {
            n = split($0, h, ",")
            for (i = 1; i <= n; i++) { if (h[i] == "FrameCompleted") col = i; if (h[i] == "IntendedVsync") iv = i }
            next
        }
        inside && col && (only == "" || win ~ only) {
            split($0, f, ",")
            c = f[col] / 1e9
            if (c > t0 && c < t0 + 4.5) printf "%.6f %.6f\n", c, c - f[iv] / 1e9
        }' | tr -d '\r' | sort -n | awk -v t0="$1" '
        NR == 1 { first = $1 }
        $2 > worst { worst = $2; open = $1 }
        END {
            if (first) printf "first=%dms open=%dms slowest-frame=%dms", (first - t0) * 1000, (open - t0) * 1000, worst * 1000
            else printf "no-frames"
        }'
}

panel_run() {
    local log script
    log=$(mktemp); script=$(mktemp)
    panel_uinput_script >"$script"
    adb_ shell input keyevent 224 # a screen saver takes focus and the keys
    adb_ shell am force-stop "$PKG"
    sleep 2
    adb_ shell am start -W -a android.intent.action.VIEW -d "$PANEL_VIDEO" -n "$PKG/$SPLASH" >/dev/null
    local waited=0
    while ! adb_ shell dumpsys media_session | grep 'state=PLAYING' >/dev/null && [ "$waited" -lt "$PANEL_WAIT" ]; do
        sleep 3
        waited=$((waited + 3))
    done
    [ "$waited" -lt "$PANEL_WAIT" ] || echo "  warning: not playing after ${PANEL_WAIT}s"
    sleep 5
    adb_ shell 'timeout 90 getevent -lt' >"$log" &
    sleep 1
    adb_ shell uinput - <"$script" >/dev/null &
    # A dump falls inside each state, a dialog's window is gone once it closes. uinput takes seconds to start on
    # a weak device, so this goes by the presses in the log and not by the clock.
    local d1 d2 d3 d4
    after_press() {
        while [ "$(grep -c 'EV_KEY.* DOWN' "$log")" -lt "$1" ]; do sleep 0.2; done
        sleep "$2"
        adb_ shell dumpsys gfxinfo "$PKG" framestats
    }
    d1=$(after_press 1 3)  # panel 1
    d2=$(after_press 3 3)  # panel 2
    d3=$(after_press 8 5)  # dialog 1
    d4=$(after_press 15 5) # dialog 2
    wait %2 2>/dev/null || true
    kill %1 2>/dev/null || true
    local downs node
    # The presses of perf-dpad, in order: its node is the one that reports KEY_SELECT.
    node=$(tr -d '\r' <"$log" | awk '/KEY_SELECT/ { gsub(/:/, "", $3); print $3; exit }')
    downs=$(tr -d '\r[]' <"$log" | awk -v node="$node:" '$2 == node && $3 == "EV_KEY" && $5 == "DOWN" { print $1 }')
    # Some kernels stamp the events of a reader that asked for no clock in real time, and frames are in uptime.
    local offset
    offset=$(adb_ shell 'echo $(date +%s.%N) $(cut -d" " -f1 /proc/uptime)' | tr -d '\r' | awk '{ printf "%.3f", $1 - $2 }')
    t() { echo "$downs" | sed -n "$1p" | awk -v o="$offset" '{ printf "%.6f", ($1 > 1e9 ? $1 - o : $1) }'; }
    echo "  panel 1:  $(echo "$d1" | frames_after "$(t 1)" MaterialPlaybackActivity)"
    echo "  panel 2:  $(echo "$d2" | frames_after "$(t 3)" MaterialPlaybackActivity)"
    echo "  dialog 1: $(echo "$d3" | frames_after "$(t 8)" MaterialDialogActivity)"
    echo "  dialog 2: $(echo "$d4" | frames_after "$(t 15)" MaterialDialogActivity)"
    adb_ shell am force-stop "$PKG"
    rm -f "$log" "$script"
}

if [ "${MODE:-feed}" = panel ]; then
    refuse_if_playing
    echo "serial=$SERIAL pkg=$PKG panel and dialog, $PANEL_VIDEO"
    for run in $(seq 1 "$RUNS"); do
        echo "run $run:"
        panel_run
    done
    adb_ shell input keyevent 3
    exit 0
fi

refuse_if_playing
echo "serial=$SERIAL pkg=$PKG input=$INPUT step=${STEP}ms"
echo "compile state: $(adb_ shell dumpsys package dexopt | grep -A2 "\[$PKG\]" | grep -o 'status=[^]]*' | head -1)"

results=$(mktemp)
for run in $(seq 1 "$RUNS"); do
    refuse_if_playing
    adb_ shell input keyevent 224 # a screen saver takes focus and the keys
    adb_ shell am force-stop "$PKG"
    sleep 2
    start=$(adb_ shell am start -W ${VIEW:+-a android.intent.action.VIEW -d "$VIEW"} -n "$PKG/$SPLASH" | tr -d '\r' | awk '/TotalTime/ { print $2 }')
    sleep "$SETTLE"
    for key in $ENTER; do
        adb_ shell input keyevent "$(keycode "$key")"
        sleep 1
    done
    sleep 2
    measured_moves
    focus=$(adb_ shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r' | sed 's/.* //; s/}$//')
    line=$(adb_ shell dumpsys gfxinfo "$PKG" | read_gfx)
    echo "run $run: start=${start}ms $line focus=$focus"
    echo "$line" >>"$results"
    if [ -n "$SHOT" ]; then
        mkdir -p "$SHOT"
        adb_ exec-out screencap -p >"$SHOT/run$run.png"
    fi
done
adb_ shell input keyevent 3
summary <"$results"
rm -f "$results"
