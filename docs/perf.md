# Performance on real devices

Devices: a 4K Google TV stick ("projector", signed in) and a Chromecast with Google TV `sabrina` (signed out). Both are
32-bit, 2 GB RAM, Android 14, at 1080p UI. The Material app is the `stmaterialRelease` armeabi-v7a APK with R8 off.

**Result: scrolling meets the target on both devices once the app's profile is compiled, and is at least as good as the
old SmartTube on the same screen. Until the system compiles it, about the first 30 s of scrolling after each app start
stay poor.** The cause was the runtime, not the UI: the sideloaded app ran interpreted.

## How it is measured

`scripts/perf.sh <serial> [runs]`: cold start, 25 s to let the feed load, reset `dumpsys gfxinfo`, 19 D-pad moves 300 ms
apart (right x10, down, right x5, down, up, up), read gfxinfo. Tables give the median of 3 runs.

- Same screen for every build: the channel `UCX6OQ3DkcsbYNE6H8uQQuVA`, opened by link, because a signed-out Chromecast has
  no other feed (Subscriptions, Home, History and Playlists are empty there). It also shows the same cards in the old app.
  `VIEW= ENTER=down` measures the home screen instead (needs an account).
- Keys come from one `uinput` process, because `adb shell input` starts a Java process per key. On the projector that
  moved the old app from 7.6% to 13.9% janky, p90 unchanged; on the Chromecast the Material app did not change
  (7.9% against 9.1%). `INPUT=input` reproduces it.
- `MODE=panel` measures the panel and the dialog: key time from `getevent`, frame time from `gfxinfo framestats`.

## Feed scrolling, before and after

"Before" is the state after `adb install -r`: `status=verify`, no compiled code. "After" is this commit, after first launch
and `cmd package compile -m speed-profile`, which is what the system's own job runs.

| Device, screen | Build | Janky | p50 | p90 | p99 | Slow UI | Slow bitmap | Slow draw | Frames |
|---|---|---|---|---|---|---|---|---|---|
| Projector, channel | before | 94.12% | 81 | 150 | 400 | 78 | 0 | 0 | 85 |
| Projector, channel | **after** | **8.02%** | **16** | **24** | **36** | 21 | 0 | 0 | 261 |
| Projector, channel | old SmartTube (`speed-profile`) | 7.61% | 23 | 36 | 85 | 12 | 1 | 22 | 289 |
| Projector, Home | library and own rules, before the tab fix | 11.02% | 17 | 28 | 42 | 24 | 0 | 1 | 264 |
| Projector, Home | **after** | **9.09%** | **16** | **26** | **36** | 22 | 0 | 0 | 264 |
| Chromecast, channel | before | 80.36% | 61 | 101 | 200 | 93 | 0 | 0 | 112 |
| Chromecast, channel | **after** | **9.13%** | **19** | **22** | **38** | 22 | 0 | 0 | 263 |

Times in ms. The target was a p90 near the old app (42 ms, 21% janky in the first baseline, 36 ms, 7.6% on this screen).
The old app was measured on the projector only. A last run of the final code, not in the table: Chromecast 7.81%, 19/22/34;
projector, one run, 6.90%, 16/20/38. About one frame per key press stays slow, on both apps; the rest of a scroll is inside the frame budget.

Cold start, `TotalTime` by link, ms, 3 runs: projector before 1963, 920, 795; after 1223, 1159, 695. Chromecast before
715, 655, 655; after 863, 1075, 775. The start did not improve: the same range on the projector, about 150 ms slower on the
Chromecast. Single runs reached 2219 and 3467 ms after compiling. The cause was not looked into.

## First player panel and first dialog

Key press to the frame that shows it, ms, median of 3 runs, one video. A compiled projector:

| | Before | After |
|---|---|---|
| Panel, first open | 430 | 249 |
| Panel, second open | 200 | 72 |
| Quality dialog, first open | 2273 | 885 |
| Quality dialog, second open | 1693 | 492 |

The dialog is an activity of its own, so it costs more than the panel. Its slowest frame after compiling is 314-456 ms on
the first open. Both still are a pause rather than instant, and the dialog is the one to look at next.

On the signed-out Chromecast the video did not reach `PLAYING` in any of 9 starts within 60 s: where it was looked at, the
player sat on its spinner with "Fixing stalled client". That needs its own look. The dialog still opens there: 506 ms compiled
against 1169 before, first open; 659 against 1306 second. The panel numbers are not usable, because they ran behind that busy
player (over 1.3 s in both builds).

## What was checked, in the order of the brief

### 1. No compiled code: this was the cause

| Build | Device | Janky | p50 | p90 | p99 | Slow UI |
|---|---|---|---|---|---|---|
| `verify` | Chromecast | 80.36% | 61 | 101 | 200 | 93 |
| library rules, `speed-profile` | Chromecast | 11.67% | 19 | 27 | 53 | 29 |
| library and own rules, `speed-profile` | Chromecast | 9.43% | 19 | 23 | 36 | 22 |
| whole app, `speed` | Chromecast | 8.61% | 19 | 23 | 36 | 21 |
| `verify` | Projector | 94.12% | 81 | 150 | 400 | 78 |
| library and own rules, `speed-profile` | Projector | 8.78% | 16 | 24 | 40 | 21 |
| whole app, `speed` | Projector | 7.66% | 16 | 22 | 34 | 19 |

- `profileinstaller` was already in the app through Compose, with its initializer in the merged manifest, and the logcat
  shows `Installing profile` on first launch. No new dependency is needed.
- The libraries' rules left out most of our own code and much of Compose. `src/stmaterial/baselineProfiles/baseline-prof.txt`
  adds our package, all of Compose, `androidx.collection`, coroutines, Kotlin and Glide. It reaches `speed` numbers without
  compiling the whole 42 MB of dex: 28 s on the Chromecast and 47 s on the projector for `speed-profile`, against 53 s for
  `speed` on the Chromecast. AGP ignores `src/stmaterial/baseline-prof.txt`, the file must be in that directory.
- `baseline.prof` in the APK became smaller (3.4 KB from 8.9 KB) with more rules, because long runs of set bits compress well.
  The numbers above, not its size, are the evidence that it works.

**The wait.** The profile is written on first launch, but the code is compiled by the system's
`BackgroundDexoptJobService`: once a day at most, charging and idle. On these devices other apps show `reason=bg-dexopt`, so
it runs, but it can take up to about a day, and every update starts over. After `cmd package compile --reset` a later `speed-profile`
stayed at `verify` here, so the profile is gone and the installer did not write it again: test after a fresh `install -r`.

Until then the JIT does the work in each process. Chromecast, `verify`, same process, the same 19 moves again and again:

| Pass | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Janky | 85.5% | 48.3% | 24.6% | 16.8% | 11.8% | 13.9% |
| p90, ms | 113 | 65 | 44 | 40 | 36 | 36 |

A pass takes about 9 s. So a new install is slow for about the first 30 s of scrolling after each start, for up to a day. To skip the wait:
`adb shell cmd package compile -m speed-profile io.github.ivanosh.smarttubematerial` after the first launch.

### 2. Recomposition: one fix, small effect

Temporary counters in a local build, projector, Home, the 19 moves:

| Composable | Before | After |
|---|---|---|
| `BrowseScreen`, `Tabs` | 4 | 2 |
| `BarButton` | 20 | 10 |
| `FeedCard`, `VideoCard` | 70 | 64 |

`BrowseScreen` read `focusedRow` for the tabs animation, so any move between rows recomposed it and all tabs. It now reads a
derived value; the 2 left are the moves that do show or hide the tabs. Scrolling changed within the noise of the runs
(11.02% to 9.09%, p90 28 to 26, with runs from 7.6% to 11.7% for the same build). A focus change recomposes two cards, the
rest are new cards. Focus scale already is in `graphicsLayer`. Nothing more was changed: the profile below shows no single
expensive composable.

### 3. Card: keep the one bitmap

The card with its title and meta line as Compose text, thumbnail and badge still baked, so the least change from today:

| Device | Card | Janky | p50 | p90 | p99 | Slow UI |
|---|---|---|---|---|---|---|
| Chromecast | one bitmap | 9.43% | 19 | 23 | 36 | 22 |
| Chromecast | Compose text | 10.27% | 20 | 31 | 48 | 22 |
| Projector | one bitmap | 8.78% | 16 | 24 | 40 | 21 |
| Projector | Compose text | 12.20% | 17 | 28 | 48 | 30 |

The text is what a plain card would add, and it is already worse on both devices. The bitmap stays. It is the only card in
the code, so there was nothing to remove. The Material app had no slow bitmap uploads in any run.

### 4. Presenter work at focus: not a cost

`BrowsePresenter.onVideoItemSelected` and `ChannelPresenter.onVideoItemSelected` only store a field or nothing. In a profile
of the projector's Home, `onVideoItemSelected` is not among the listed items, and all of `BrowsePresenter` is 1.3% of the main thread, a page
arriving. Tab switching itself was not profiled: its load is debounced by 250 ms and its requests are asynchronous. Nothing was changed.

### 5. Key injection

See "How it is measured". The same method is used for all the numbers above.

## Where the time goes now

`simpleperf` on a temporary profileable build (never committed), Chromecast, compiled, the 19 moves. All threads: RenderThread
28-41%, main thread 16-23%, `glide-disk-cache` 13-23% (decode and bake, off the main thread), GC 8-12%, Mali 5-10%.

On the main thread the largest single item is not ours: **`AndroidComposeViewAccessibilityDelegateCompat`, 28%
inclusive** (`getCurrentSemanticsNodes`; `semanticsChangeChecker` 24%, `boundsUpdatesEvent` 13%; on the projector's Home 32%). Compose walks the semantics
tree after every change whenever any accessibility service is on. Both devices have one: `ProjectivyAccessibilityService`,
for the launcher (plus `flar2.homebutton` on the Chromecast). So these numbers are the bad case for this app, and a TV without
such a service should do better. It was not measured without it, because that means changing the owner's settings. No public
Compose API turns this off for one service, and it must stay on for a screen reader.

Composition is 21% of the main thread, half of it new cards; layout 13%. On the projector's Home, where new rows also arrive,
composition is 40% and layout 13%.

## Proposals, not done

- **R8 on Compose and Kotlin code only.** Compose is much faster when R8 inlines its checks. Out of scope by an earlier
  ruling for the whole app; a narrow pass is not clearly safe either, because the upstream code is reflective.
- **Ship the `.dm` next to the APK.** The build already writes `baselineProfiles/*/*.dm`. `adb install-multiple base.apk base.dm`
  should compile at install and remove the wait. Not tried on the devices, whose instructions allowed only `install -r`.
- **Look at the player stall** on the signed-out Chromecast.

## Emulator, for the record

Earlier numbers from AVD `GoogleTV_34` said little, and they missed the real problem: an arm64 image on Apple Silicon, with a
GPU emulated and much faster CPU, spent its time on `Slow issue draw commands`. Do not use it to judge smoothness.

## Release APK size

`assembleStmaterialRelease`, signed with the release key. R8 is off for this variant (`minifyEnabled` is commented out,
`-dontobfuscate` in `proguard-rules.pro`), so the APKs are large.

| APK | Bytes | MiB |
|---|---|---|
| arm64-v8a | 88,578,327 | 84.5 |
| armeabi-v7a | 77,957,213 | 74.3 |
| universal | 117,950,956 | 112.5 |

The biggest entries in the arm64 APK are `libj2v8.so` (32.3 MB), five dex files (about 47 MB together) and
`libcronet` (5.2 MB). Shrinking is a later step: it needs keep rules for the reflective upstream code and a full
smoke test of a signed-in session, which the emulator cannot give.
