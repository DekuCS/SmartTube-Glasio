# SmartTube Material

A fork of [SmartTube](https://github.com/yuliskov/SmartTube) with a new D-pad-only interface for Android TV, built
with Jetpack Compose for TV: a top tab bar (no drawer), a player panel with five slots and "More", and no blur,
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
Every release is marked as a prerelease, so GitHub's "latest" link does not reach them.

## Updates

There is no built-in updater. To update, install the newer APK over the old one the same way; releases are signed with
the same key, so settings stay. A build signed with a different key must be uninstalled first.

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
4. It merges the tag with a merge commit, never a rebase or a force-push. Submodules take upstream's pins, changes under
   `.github/workflows` are dropped, and `materialPatch` goes back to 1 on a new upstream version.
5. Tests and `assembleStmaterialRelease` run on that commit in a job with no secrets and nothing writable.
6. That commit is pushed to `material` and released as a prerelease, the same way as a tagged release: see Versions.

A merge of an upstream version we already carry takes the next patch instead of 1, because the tag and the versionCode
must both be new.

The logic is in `scripts/sync/`; its tests run in CI and with `python3 -B -m unittest discover -s scripts/sync`.

What stops it, with nothing pushed:

- a changed sign-in, account or token file, in this tree or behind a submodule pin: any path with `auth`, `SignIn`,
  `account`, `token`, `credential`, `login` or `cookie` in it, and `RetrofitOkHttpHelper`. `PoToken` files do not count:
  they identify the client, not the account, and upstream changes them often.
- a changed line with a token term in any file: refresh, access or ID token, client ID or secret, `Authorization`,
  `Bearer`, `persistAccounts`, and the manifest's backup settings
- a change to `gradle/wrapper/`, `gradlew`, `buildSrc/`, `settings*.gradle*`, `gradle.properties` or a version catalog
- a changed line in Gradle code with `repositories`, `getRepositories`, `apply from`, `maven`, `url`, `uri(` or `://`,
  here or in a submodule
- a changed URL or path in `.gitmodules`, as `git config` reads it
- a pin that cannot be fetched, or a history with no single merge base
- a merge conflict
- a tag that disagrees with `versionName`, a `versionCode` that would not grow, or a release that already exists
- failed tests or a failed build

These rules prefer a false stop, which costs a look, to a false pass. Four of the last nine upstream stable steps would
have stopped.

A stop or a failure turns the run red, so GitHub sends its failure email, and opens or updates one issue per upstream
tag. Dry runs only report a stop. The issue names the files and links the compare view. The sync does not retry that
tag while the issue is open: deal with it (usually a manual merge), then close the issue. If Issues is off, the run says
why in its log and tries the tag again every 6 hours.

If a merge reached `material` and its release is missing, the next run makes the release, unless that tag's issue is
open. Nothing else is synced until then.

### What the schedule needs

GitHub fires a schedule only under these conditions, and nothing reports it when one is missing:

- The workflow is on the repository's default branch. That is `master` today, so `material` must become the default
  branch. Every run warns in its log while it is not.
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

- Performance on a Chromecast is not measured yet. Emulator numbers are in [docs/perf.md](docs/perf.md).
- The release APK is not minified, so it is large (see docs/perf.md).
- Not included: casting, voice search, several accounts, comments, Shorts, live chat, home screen channels, data
  transfer and an in-app updater.
- At most 8 tabs. Settings cannot be hidden or made the start tab.
