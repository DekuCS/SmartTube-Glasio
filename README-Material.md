# SmartTube Material

A fork of [SmartTube](https://github.com/yuliskov/SmartTube) with a new D-pad-only interface for Android TV, built
with Jetpack Compose for TV: a top tab bar (no drawer), a player panel with up to eight buttons and "More", and no blur,
parallax, auto-preview or background jobs. The playback and YouTube code is still upstream's. The English UI is the
only language.

Application id: `io.github.ivanosh.smarttubematerial`. It installs next to the original app.

## Credits and license

SmartTube is by Yuriy Liskov (yuliskov) and contributors. This fork keeps the MIT license; the notice is in
[LICENSE](LICENSE):

> MIT License. Copyright (c) 2020-present yuliskov. Permission is hereby granted, free of charge, to any person
> obtaining a copy of this software ... The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.

The `MediaServiceCore` and `SharedModules` submodules stay on their upstream URLs. Changes in `common` are marked
`// MATERIAL:`. This fork is not affiliated with YouTube or Google.

## Build

JDK 17 and the Android SDK are required.

```sh
git clone --recursive https://github.com/ivanosh/SmartTube-Material.git
cd SmartTube-Material
./gradlew :smarttubetv:assembleStmaterialRelease
```

APKs land in `smarttubetv/build/outputs/apk/stmaterial/release/` for `arm64-v8a`, `armeabi-v7a` and a universal one.
Without a signing key the build uses the debug key and prints a warning; do not share that APK. To sign with your
own key, set `MATERIAL_KEYSTORE_FILE`, `MATERIAL_KEYSTORE_PASSWORD`, `MATERIAL_KEY_ALIAS` and
`MATERIAL_KEY_PASSWORD`, or put `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in
`~/.config/smarttube-material/release.properties`. Releases are built by GitHub Actions and carry a build provenance
attestation (`gh attestation verify <apk> --repo ivanosh/SmartTube-Material`).

Release signing certificate SHA-256:
`86:19:C3:45:16:11:0B:F5:03:C6:85:0F:1C:40:57:78:FA:E8:FD:31:8B:EB:82:B1:38:DB:20:77:24:74:8E:8C`

## Install

With adb (TV in developer mode, adb over the network):

```sh
adb connect <tv-ip>:5555
adb -s <tv-ip>:5555 install SmartTube_material_<version>_<arch>.apk
```

Pick `arm64-v8a` or `armeabi-v7a` to match the device (`adb shell getprop ro.product.cpu.abilist`), or the universal APK.

Without adb: install the Downloader app on the TV, open it, and enter the URL of the APK on the newest entry of the
[GitHub releases](https://github.com/ivanosh/SmartTube-Material/releases), then allow Downloader to install unknown apps.
A release starts as a prerelease, so GitHub's "latest" link does not reach it until it is promoted; use the newest entry of the list.

## Updates

Settings has an "Updates" page with the channel and "Check now":

- **Off**: never checks.
- **Stable** (default): takes only GitHub releases that are not marked prerelease. Every release is published as a
  prerelease, so the owner promotes one by hand (edit the release, clear "prerelease"); until then Stable sees nothing
  new. Withdrawing a release (see below) takes it out of both channels.
- **Prerelease**: takes the newest release of either kind. Those builds are published without review; the page says so.

The app asks `api.github.com/repos/ivanosh/SmartTube-Material/releases` (unauthenticated, HTTPS) once a day at most, when
the browse screen is shown, and on "Check now". There is no background job. A failed check is silent, except for
"Check now", which says why. The prompt opens only over the browse screen, never over the player: a result that comes
while something else is in front waits for the next time the browse screen is shown.

Why the GitHub Releases API and not upstream's `appupdatechecker2` manifest: that manifest is a JSON file with no
checksum, no channel and no signer check, would need a separate file per channel published by the workflow, and takes
download URLs from the file. The release API already says which releases are prereleases, and for each asset it gives the
SHA-256 (`digest`) that GitHub computed at upload, so the release workflow publishes nothing extra. Upstream's updater is
still in the build but is given no URLs and is not used.

Before the system installer sees a file, all of these must hold, or nothing is installed and a short message shows:

- the download came from `github.com` or its download hosts over HTTPS (every redirect is checked; the first address is
  built from the tag and the asset name, not taken from the response), and has the size the release lists;
- its SHA-256 equals the digest of the release;
- the APK for this device's ABI (`arm64-v8a`, `armeabi-v7a`, else the universal one);
- the package manager reads it as `io.github.ivanosh.smarttubematerial`, with the version name of the release and a
  version code above the installed one (a downgrade is refused);
- its one signing certificate has SHA-256 `8619c345...748e8c` (the key above), checked in code with
  `PackageManager.getPackageArchiveInfo`.

The install goes through the system installer, which asks for confirmation; the TV asks once to allow this app to install
unknown apps. There is no silent install. The downloaded file is deleted as soon as it is handed over, and after any
failure. `REQUEST_INSTALL_PACKAGES` is declared by the `stmaterial` flavor.

Installing by hand over the old build still works the same way (see Install). A build signed with a different key must be
uninstalled first.

A debug build can be pointed at a test server (`files/update-debug.properties` with `base=` and `cert=`, written with
`adb shell run-as`). The release build has no such code.

## Versions

The upstream version stays in `smarttubetv/build.gradle` as upstream wrote it. `material-version.properties` holds this
fork's patch number, and the `stmaterial` flavor builds on both:

- `versionCode` = upstream versionCode * 100 + patch, for example 245801
- `versionName` = `<upstream>-<patch>`, for example `32.68-1`
- release tag = `material-v<versionName>`, for example `material-v32.68-1`

The other flavors keep the upstream values. To release our own changes, raise `materialPatch` (1 to 99), commit, and
push the tag. The release workflow refuses a tag that does not match `versionName`.

Releases are built with no key by a job that runs Gradle (`MATERIAL_UNSIGNED_RELEASE=true` gives unsigned APKs). A
second job signs them with the SDK's `zipalign` and `apksigner`, checks the certificate digest and publishes. It is the
only job with the key, and it never checks out or runs anything from the repository.

## Upstream sync

`.github/workflows/material-sync.yml` runs every 6 hours and can be started by hand from the Actions tab. Manual runs
are dry runs by default: they report what would happen and leave `material`, the releases and the issues alone.

1. It reads the upstream releases and takes the highest published, non-prerelease tag of the form `32.68s`.
2. It stops there if that commit is already part of `material`, or if an issue for that tag is open.
3. The gate compares upstream's tree from the last merged upstream commit, and each submodule from the pin on
   `material`, without running anything from upstream.
4. It merges the tag with a merge commit, never a rebase or a force-push. Submodules take upstream's pins, and
   `materialPatch` goes back to 1 on a new upstream version.
5. Tests and `assembleStmaterialRelease` run on that commit in a job with no secrets and nothing writable.
6. That commit is pushed to `material` and released as a prerelease, the same way as a tagged release: see Versions.

A merge of an upstream version we already carry takes the next patch instead of 1, because the tag and the versionCode
must both be new.

The logic is in `scripts/sync/`; its tests run in CI and with `python3 -I -B -m unittest discover -s scripts/sync`. The
jobs that can write run those scripts from a separate checkout of the commit before the merge, holding only that
directory, and never run anything from the merged tree.

What stops it, with nothing pushed:

- a changed sign-in, account or token file, in this tree or behind a submodule pin: any path with `auth`, `SignIn`,
  `account`, `token`, `credential`, `login` or `cookie` in it, and `RetrofitOkHttpHelper`. `PoToken` files do not count:
  they identify the client, not the account, and upstream changes them often.
- a changed line with a token term in any file: refresh, access or ID token, client ID or secret, `Authorization`,
  `Bearer`, `persistAccounts`, and the manifest's backup settings
- any change under `scripts/sync/` or `.github/`, including upstream's own workflows
- a change to `gradle/wrapper/`, `gradlew`, `buildSrc/`, `settings*.gradle*`, `gradle.properties` or a version catalog
- a changed line in Gradle code with `repositories`, `getRepositories`, `apply from`, `maven`, `url`, `uri(` or `://`,
  here or in a submodule
- a change under `smarttubetv/src/stmaterial*` or `testStmaterial`, or to `material-*` files and `README-Material.md`:
  the flavor holds the updater and its pinned certificate
- any `AndroidManifest.xml`, here or behind a submodule pin, since a manifest decides what is exported
- any path with `backup` in it, here or behind a submodule pin, since backup code decides what leaves the device
- any path with `appupdate` or `update_urls` in it
- a changed line with `signingConfig` or `update_urls` in any file
- a changed URL or path in `.gitmodules`, as `git config` reads it
- a pin that cannot be fetched, or a history with no single merge base
- a merge conflict
- a tag that disagrees with `versionName`, a `versionCode` that would not grow, or a version not newer than the newest
  `material-v*` tag or release
- failed tests or a failed build

A new Gradle dependency or plugin from an allowed repository does not stop the sync; it is only in what a prerelease signs, which the owner has accepted.

These rules prefer a false stop, which costs a look, to a false pass. Five of the last nine upstream stable steps would
have stopped under these rules (measured from 31.57s to 32.68s, the same five as before the flavor, manifest, backup and
updater rules were added, which only add reasons to them), so expect to look at about half of them.

A stop or a failure turns the run red, so GitHub sends its failure email, and opens or updates one issue per upstream
tag. Dry runs only report a stop. The issue names the files and links the compare view. The sync does not retry that
tag while the issue is open. To clear a stop, merge the tag into `material` by hand after looking at it, or fix what
stopped it, then close the issue; closing it without that only makes the next run stop again. If Issues is off, the run
says why in its log and tries the tag again every 6 hours.

If a merge reached `material` and its version was never published, the next run makes the release, unless that tag's
issue is open or a newer version exists. Nothing else is synced until then. It first rebuilds that merge from its two
parents, the second of which must be the upstream tag's commit, with the gate; a different tree or a stop means no
release. To clear that stop, push the release tag at the merge if it is good, or raise `materialPatch` and push a newer
tag if it is not, then close the issue. Later syncs wait until then.

### Turning the sync on and off

The sync publishes only while the repository variable `MATERIAL_SYNC_ENABLED` is exactly `true` (Settings, Secrets and
variables, Actions, Variables). Unset or any other value, `TRUE` included, is off: changing the default branch arms
nothing by itself. While off, `decide` still runs and shows a notice, and dry runs still build and test. Non-dry runs skip
verify, push, release and issue, so a stop shows only in that run's summary and does not turn it red. A tag you push by
hand needs no variable.

`decide` checks the exact value in shell, because GitHub's `==` ignores case. Each later job checks the variable in its
own `if`, and so does the signing job when the sync calls it. GitHub's documentation says an `if` is processed before the
job is sent to a runner, and that an unset variable reads as an empty string. It does not say whether a job sees a value
changed after the run started, and this has not been tested here. So treat removing the variable as applying to the next
run, and cancel any run in progress to stop it for certain.

### Withdrawing a release

A tag only ever comes from a successful release, so an existing tag means that version is done:

- To withdraw a release, delete it on the Releases page and keep the tag. It is not published again.
- To publish that version again, delete both the release and the tag (`git push origin :refs/tags/material-v32.68-1`).
  The next run rebuilds it, so remove `MATERIAL_SYNC_ENABLED` first if you do not want that yet.
- A version lower than the newest tag is never published by the sync. After raising `materialPatch` and tagging by hand,
  the older merge stays unpublished.

### What the schedule needs

GitHub fires a schedule only under these conditions, and nothing reports it when one is missing. A run that fires still
publishes only if `MATERIAL_SYNC_ENABLED` is `true`.

- The workflow is on the repository's default branch. That is `material`; every run warns in its log if it is not.
- Actions and scheduled workflows are enabled in the Actions tab. GitHub turns them off in a fork until they are
  enabled by hand, and this repository is a fork.
- The repository saw activity in the last 60 days; otherwise GitHub pauses the schedule.

The failure email for a scheduled run goes to whoever last changed the workflow's `cron` line, and follows that
person's notification settings.

## Sign-out and revoking access

Signing out in the app, or uninstalling it, does **not** revoke the access token. To revoke it, open
[myaccount.google.com/security](https://myaccount.google.com/security), go to "Your connections to third-party apps &
services", find YouTube TV and choose "Remove access".

## Known limits

- Performance numbers, from an emulator and from a Chromecast with Google TV and a 4K stick, are in [docs/perf.md](docs/perf.md).
- On a signed-out Chromecast, videos stayed on a spinner with "Fixing stalled client" (the SABR streaming stall). That is in
  the playback engine, not the new interface, and is not fixed here.
- The release APK is not minified, so it is large (see docs/perf.md).
- Not included: casting, voice search, several accounts, comments, Shorts, live chat, home screen channels, data
  transfer.
- At most 8 tabs. Settings cannot be hidden or made the start tab.
