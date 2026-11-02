# SmartTube Material

A personal Android TV client: a new D-pad-only interface in Jetpack Compose for TV on a fork of
[SmartTube](https://github.com/yuliskov/SmartTube) (upstream author: Yuriy Liskov). Playback and YouTube code are still
upstream's. Branch `material` is the default branch. User docs, build, install, updater, versions and sync are in
[README-Material.md](README-Material.md); read it, it is not repeated here. Measurements: [docs/perf.md](docs/perf.md).
The repo is public: no IP addresses, serials, emails, tokens, home-directory paths or video titles in files or commits.
Refer to devices by kind (emulator, Chromecast with Google TV, Google TV stick).

## What is ours and what is upstream's

- Ours is the `stmaterial` product flavor (application id `io.github.ivanosh.smarttubematerial`, minSdk 29, ARM ABIs only).
  Code: `smarttubetv/src/stmaterial/java/io/github/ivanosh/stmaterial/`. Tests: `smarttubetv/src/testStmaterial/`.
  Other flavors are upstream's and keep upstream values.
- Submodules (`MediaServiceCore`, `SharedModules`) stay on upstream URLs and pins. The sync gate stops on any change to them.
- Edits in `common` (and other upstream modules) are minimal and marked `// MATERIAL:`. Keep each file's line endings:
  `common` mixes CRLF and LF, so check `git ls-files --eol <file>` and do not let an editor convert it.
- Flavor gate: `SidebarService.isMaterialUi()` in `common`, switched on by `enableMaterialUi()` in `MaterialApplication`.
  Code in `common` that behaves differently for us asks it; other flavors never turn it on.
- New screens: implement upstream's `*View` interface in an Activity under `stmaterial`, then register it in
  `MaterialApplication.registerViews()` with `ViewManager.register(View::class.java, Activity::class.java, parent)`.
  Order matters there: the parent lookup goes by `isInstance`, so the `PlaybackView` registration comes last.
  Activities are declared in `src/stmaterial/AndroidManifest.xml`, which also removes upstream components we do not ship.

## Commands

```sh
python3 -I -B -m unittest discover -s scripts/sync                                   # sync gate and workflow tests, ~70 s
./gradlew :smarttubetv:testStmaterialDebugUnitTest :common:testStfdroidDebugUnitTest # what CI runs
./gradlew :smarttubetv:assembleStmaterialRelease                                      # APKs in smarttubetv/build/outputs/apk/stmaterial/release/
scripts/emulator.sh start | install | shot <file> | key <keycode...>                  # AVD GoogleTV_34 on a fixed port
scripts/perf.sh <serial> [runs]                                                       # feed scrolling via gfxinfo; header lists env options
```

- Release builds without a key use the debug key and warn; never share that APK.
- `scripts/emulator.sh install` is the way to put a debug build on the emulator. It uses a fixed serial.
- `actionlint` is not installed here and CI does not run it; `scripts/sync/test_workflows.py` checks the workflows' shape.
- Do not run Gradle if another agent is building in the same checkout.

## Architecture in brief

- `logic/`: pure Kotlin (button lists, key tables, back navigation, SponsorBlock skip and undo, update rules, track
  geometry). No Android types, so JVM tests cover it. Put new decisions here and test them first.
- Screens: `browse/`, `channel/`, `search/`, `dialog/`, `settings/`, `signin/`, `player/`, `update/`, shared widgets in
  `ui/`. State holders (`*State.kt`) are written by the Activity and read by Compose.
- Player: `MaterialPlaybackActivity` extends upstream's playback Activity and keeps its Leanback fragment alive and hidden
  under a Compose overlay (`PlayerOverlay`, state in `PlayerOverlayState`). `positionMs` changes every tick, so the
  progress bar and time are read inside `Canvas` / draw lambdas, not in composition: the panel does not recompose per tick.
  Keep it that way when adding anything that follows position.
- Traps in `MaterialPlaybackFragment`: Leanback shows its controls through many routes; `showControlsOverlay` is a no-op
  and the panel is driven by `showOverlay` / `showControls`. `setBackgroundResource` is overridden to a no-op, because
  Leanback's gradient is set at full alpha and only its own fade (which never runs here) cleared it, so it stayed over
  the video. Leanback's progress bar is replaced by our spinner in its progress manager (1 s delay is Leanback's).
- Cards (`ui/VideoCard.kt`) are one bitmap each, drawn off the main thread and cached in Glide's memory cache. This was a
  measured choice (Compose text per card scrolled worse on real devices). Do not turn cards back into Compose text without
  re-measuring with `scripts/perf.sh` on a Chromecast.
- Baseline profile: `src/stmaterial/baselineProfiles/baseline-prof.txt`. The sideloaded app runs interpreted until
  compiled, so scrolling is poor for about 30 s after install. Judge performance after
  `adb -s <serial> shell cmd package compile -m speed-profile <package>`. See docs/perf.md.

## Facts that cost time

- Layout canvas is 1920 units wide on every device. `MaterialTvTheme` gives Compose a `Density` of widthPixels / 1920, so
  1.dp is 1/1920 of the screen width, not the usual 960dp-wide TV. Leanback Views and `resources.displayMetrics` keep the
  real density: convert when mixing (the spinner does).
- Screenshots (`screencap`) do not capture the video layer. A visual bug over video cannot be seen in them; reason from code
  or ask the owner to look.
- Activities such as browse, dialog and player are `singleInstance` and are reused. Per-screen key state (for example the OK
  hold in the player) survives into the next showing, so reset it in `onPause` / `onStop`.
- A debug build cannot replace a release-signed install (signature mismatch). Use the emulator for debug, or install release.
- `adb shell input keyevent --longpress` is not a real remote hold (no repeats from the remote). Long-press code fires on key
  repeats or `isLongPress`; test it with `scripts/perf.sh`-style `uinput` or on a real remote.
- `adb shell input` starts a Java process per key and distorts timing on weak devices.
- Presenters in `common` map the Settings section once at creation, so `enableMaterialUi()` must run before any presenter.

## Constraints and anti-goals

- Light UI. No blur, parallax, autoplay previews, scrim over video, or background jobs. The update check runs at most daily
  from the browse screen and on request; no WorkManager.
- D-pad only. A gesture that needs a long press must have a visible alternative (the editors are reachable from Settings).
- Limits: 8 tabs (`Sections.LIMIT`, Settings is outside the count); 8 player buttons plus More (`PANEL_LIMIT`); the default
  eight are the old five plus Like, Dislike, Subscribe.
- English is the only UI language. All UI text in English.
- The account token is never in shared storage or backups: `allowBackup="false"`, and the token is not exported. The sync gate and
  the updater guard this.
- DIAL (cast) and `leanbackassistant` (home channels, search provider) are off: components removed in the manifest,
  `Utils.disableChannelsService()` and `disableQrService()` called at startup.
- Not included: casting, voice search, several accounts, comments, Shorts, live chat, data transfer.

## Release and sync

- Version: `versionCode = upstream * 100 + materialPatch`, `versionName = <upstream>-<patch>`, from
  `material-version.properties`. Tag `material-v<versionName>` (for example `material-v32.68-1`); the workflow rejects a
  tag that disagrees with `versionName`.
- Every release is a prerelease. The owner promotes it to Stable by hand. Withdraw by deleting the release, keep the tag.
- Build job has no secrets and runs Gradle; a separate job holds the key, runs only `zipalign` / `apksigner` and never checks
  out repo code. Keep that split.
- Upstream sync (`material-sync.yml`) merges upstream's tag with a merge commit, never rebase, after the gate in
  `scripts/sync/`. It publishes only while repository variable `MATERIAL_SYNC_ENABLED` is exactly `true`.
- The updater pins the release signing certificate in code and checks digest, package, version code and size before the system
  installer. Changing the gate, `update/`, `logic/AppUpdate.kt`, signing, or workflows is security-sensitive: the gate itself
  stops on changes to them. Say so in the commit message, keep changes small, and run the sync suite.

## Working rules for agents

- Commits carry no Claude attribution (no Co-Authored-By, no "Generated with"). The commit identity is the GitHub noreply
  address, already configured; do not change it.
- Never force-push `material` and never touch `master` unless the owner says so. No rebase, reset or stash of shared state.
  Commit only the files you changed, by explicit path.
- On a device signed in to the owner's account: `adb install -r` only. Never clear data, read tokens or shared prefs, interrupt
  playback, like, subscribe or do any other account write. Open only videos from the Subscriptions tab.
- Always `adb -s <serial>`: a phone and other devices may be attached. Never run Gradle `install*` tasks (they hit every device).
- Logic changes: TDD with a real RED run first (see the test fail for the right reason), then GREEN.
- Screens that need a feed: use the emulator or a signed-out device and a channel opened by link; do not use the owner's account
  for screenshots that go into the repo.
