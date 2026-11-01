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
`~/.config/smarttube-material/release.properties`. Releases are built by GitHub Actions on tags `material-v*`
and carry a build provenance attestation (`gh attestation verify <apk> --repo ivanosh/SmartTube-Material`).

Release signing certificate SHA-256:
`86:19:C3:45:16:11:0B:F5:03:C6:85:0F:1C:40:57:78:FA:E8:FD:31:8B:EB:82:B1:38:DB:20:77:24:74:8E:8C`

## Install

With adb (TV in developer mode, adb over the network):

```sh
adb connect <tv-ip>:5555
adb -s <tv-ip>:5555 install SmartTube_material_<version>_<arch>.apk
```

Pick `arm64-v8a` or `armeabi-v7a` to match the device (`adb shell getprop ro.product.cpu.abilist`), or the universal APK.

Without adb: install the Downloader app on the TV, open it, and enter the URL of the APK on the latest
[GitHub release](https://github.com/ivanosh/SmartTube-Material/releases/latest), then allow Downloader to install unknown apps.

## Updates

There is no built-in updater. To update, install the newer APK over the old one the same way; releases are signed with
the same key, so settings stay. A build signed with a different key must be uninstalled first.

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
