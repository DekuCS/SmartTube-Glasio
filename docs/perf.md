# Feed performance (preliminary)

Emulator only. These numbers say little about a real TV: AVD `GoogleTV_34`, headless, arm64 image on an
Apple Silicon host, GPU emulated (`ro.hardware.egl=emulation`). The verdict waits for a Chromecast.

## How it was measured

`dumpsys gfxinfo io.github.ivanosh.smarttubematerial reset`, then a scripted pass over a signed-out feed
(Gaming tab, 3 rows and more, thumbnails loading from the network), then `dumpsys gfxinfo`. A pass is three times
`22×6 21×6 20`, then the D-pad held for 1.5 s right, left, down and up. Three passes per launch, cold start first.
"Janky" is the `Janky frames` line, not the legacy one.

## Results

Same host, same scenario. The batches differ because the host's load changed between them.

| Build | Card | Host | Pass 1 | Pass 2 | Pass 3 | Slow draw frames, pass 3 | Slow UI frames, pass 3 |
|---|---|---|---|---|---|---|---|
| debug | plain Compose, corners baked | quiet | 55.50% | 23.71% | 12.81% | 20 | 13 |
| debug | one bitmap | quiet | 26.21% | 13.94% | 13.27% | 19 | 11 |
| release | plain Compose, corners baked | quiet | 16.83% | 10.55% | 10.61% | 15 | 3 |
| release | one bitmap | quiet | 14.42% | 10.53% | 10.27% | 17 | 2 |
| release | plain Compose, corners baked | busy | 23.88% | 15.90% | 14.49% | 26 | 14 |
| release | one bitmap | busy | 21.08% | 18.32% | 15.42% | 27 | 8 |

Percentiles for the one-bitmap release, quiet host, pass 3: 50th 25 ms, 90th 32 ms, 99th 53 ms.

Control: the old Leanback screen, release, quiet host: 12.44%, 5.57%, 5.44%, with 18 slow draw frames in each of
the last two passes. It was not a fair feed: signed out, the same keys only walked its sidebar next to an empty Home.
It still lost the same number of frames as the new screen. Those look like the emulator's floor. The new screen
draws fewer frames per pass (about 190 against 340), so the same loss reads as a larger percentage.

## Reading

- The brief's threshold is 10%. Every batch is above it, so cards were simplified to one bitmap as the brief says.
- The bitmap did not measurably reduce jank here. The difference is within the noise, and a busier host moves the
  result more than the card does. It did not hurt either, and it keeps the UI thread almost out of the slow frames.
- What remains is `Slow issue draw commands`, which is the emulated GPU.
- A baked card has costs to weigh on real hardware: the text appears together with the thumbnail, and it is
  slightly soft during the 1.04 focus scale. If a Chromecast shows the plain card is fast enough, it is the simpler one.

## Release APK size

`assembleStmaterialRelease`, signed with the release key. R8 is off for this variant (`minifyEnabled` is commented out,
`-dontobfuscate` in `proguard-rules.pro`), so the APKs are large.

| APK | Bytes | MiB |
|---|---|---|
| arm64-v8a | 88,134,023 | 84.1 |
| armeabi-v7a | 77,512,909 | 73.9 |
| universal | 117,506,652 | 112.1 |

The biggest entries in the arm64 APK are `libj2v8.so` (32.3 MB), five dex files (about 47 MB together) and
`libcronet` (5.2 MB). Shrinking is a later step: it needs keep rules for the reflective upstream code and a full
smoke test of a signed-in session, which the emulator cannot give.

## To measure on a Chromecast

Install the release APK (not debug), with the TV in developer mode and `adb` over the network.

```sh
TV=<tv-ip>:5555
adb connect $TV
adb -s $TV install -r SmartTube_material_<version>_armeabi-v7a.apk   # arm64-v8a if `getprop ro.product.cpu.abilist` starts with it
PKG=io.github.ivanosh.smarttubematerial
SPLASH=$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
```

Cold start (3 runs, `TotalTime` and `WaitTime` are the numbers to keep):

```sh
for i in 1 2 3; do adb -s $TV shell am force-stop $PKG; sleep 2; adb -s $TV shell am start -W -n $SPLASH | grep -E 'TotalTime|WaitTime'; done
```

Feed jank, same scenario as above (three passes per launch, cold start first):

```sh
adb -s $TV shell dumpsys gfxinfo $PKG reset
# one pass: D-pad keys, 0.3 s apart (22 right, 21 left, 20 down, 19 up)
for k in 22 22 22 22 22 22 21 21 21 21 21 21 20 20 20 19 19 19; do adb -s $TV shell input keyevent $k; sleep 0.3; done
adb -s $TV shell dumpsys gfxinfo $PKG | grep -E 'Total frames|Janky frames|50th|90th|95th|99th|Number Slow'
```

Two interactions to measure separately, because the emulator showed them slow:

1. First panel open: 1.3 s on the emulator. Start a video, wait for playback, then record and open the panel.
2. First dialog open: the first time a Quality, Subtitles or Speed dialog opens after the player starts. Repeat opens are
   expected to be faster, so note first and second separately.

```sh
adb -s $TV shell screenrecord --time-limit 12 --bugreport /sdcard/open.mp4 &
sleep 3
adb -s $TV shell input keyevent 20      # opens the panel; for the dialog, press 23 on Quality instead
wait
adb -s $TV pull /sdcard/open.mp4 . && adb -s $TV shell rm /sdcard/open.mp4
```

Read the time between the key press and the first frame with the panel (or dialog) fully drawn, by stepping the recording
(`ffmpeg -i open.mp4 -vf fps=30 f_%03d.png`). `dumpsys gfxinfo $PKG framestats` over the same window shows the slow frames.
Do three runs each and keep the median. Then replace this file's numbers with the Chromecast ones.
