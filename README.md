# SWTV Custom Home

Native Kotlin/Android Views launcher for the inspected SWTV-20AE Android10 TV. Application ID `app.swtv.home`; minimum Android10/API29. This repository contains original launcher code. The referenced launchers/players were inspected for behavior; their source, images and installer/debloat scripts were not adopted.

## Build

Use JDK17, Android SDK35 and Build Tools34.0.0. The Gradle wrapper pins Gradle8.9 and its SHA-256. Open this folder in Android Studio, select JDK17 and let the SDK manager resolve the listed SDK packages. Android Studio will create an untracked `local.properties` for your SDK path.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest
python scripts/verify-module-boundaries.py
```

The prepared task environment can instead use:

```powershell
.\build-local.ps1
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The handoff directory outside the source project contains a named copy and SHA-256. A debug-signed APK is a development artifact, not a production release; keep debug and future release application/signing identity decisions explicit before any deployment.

See `docs/TOOLCHAIN.md` for tool sources and version checks, and `docs/VALIDATION.md` for the actual checks performed and their limits.

## Expected use

Open SWTV Home through the TV's normal app grid after a separately approved ordinary-app installation. A HOME intent entry is declared so the app can be selected through the system in a later phase; installation does not request or force the default. Long-press OK on an app for pin/hide actions. Settings manage dock order, app-grid columns, themes, motion, wallpaper, module tiles, weather and the IPTV shortcut target.

The dedicated **OwnTV** tile defaults to package `tv.own.owntv`, verified from [the requested GitHub project's build configuration](https://github.com/Thedurancode/owntv/blob/main/app/build.gradle.kts). If OwnTV is absent, choose another installed app or explicitly open [OwnTV upstream releases](https://github.com/ahXN00/OwnTV/releases) in a browser. The launcher does not download or install OwnTV. A separately provisioned player must support the TV's 32-bit ARM app ABI. Package lookup and an ordinary launch shortcut do not embed any OwnTV code.

The separate **TV Hub** tile reserves `app.swtv.tvhub` for future development through a versioned, explicit navigation contract. See [the provider interface](docs/TV_HUB_INTERFACE.md). There is no TV Hub APK, playlist ingestion, EPG synchronization or streaming/playback implementation in this launcher.

The Cyberpunk HUD uses charcoal panels, electric-yellow accents and cyan focus borders. Select **Settings → Theme → Dark** for this appearance; existing Light/Auto preferences are retained. Motion follows the reduced-motion setting and device policy.

**Web apps → Add website** saves an HTTPS address as a home tile. Open it in SWTV's own full-screen viewer, or use Android **Share → Add to SWTV** when the sending browser supports Share. Hold OK on a website tile to rename, edit, move or remove it. The viewer's Bookmark control can save the current page. See [website tiles and controls](docs/WEB_APPS.md).

HDMI/TV shortcuts enumerate device TV inputs and use the platform's typed channel intent. OEM permission and remote behavior require validation on the actual TV before claiming input switching works there. Unavailable targets produce an explanatory dialog instead of altering firmware or app state.

## Privacy and settings

Configuration is app-local. Weather is off by default and requires an explicit user-entered location and opt-in. Enabled weather uses Open-Meteo HTTPS endpoints; no device location permission is requested. Clock/date follow the TV's locale/time zone. Weather attribution is displayed in the app.

The Diagnostics control collects an on-demand snapshot of public device/build, display, memory and app-filesystem information. About reports the installed version and update status. Development builds show the unconfigured feed. Configured releases check their dedicated public HTTPS feed only when the user asks, verify the APK, and hand installation to Android for confirmation.

No accessibility remapper, root access, boot receiver, persistent background service, system settings writes or package-disable functionality belongs to this project. System Settings and browser links open only after user action.

## Getting the best result

- **Install it as an ordinary app and keep the stock launcher as default**
  until you have tested remote navigation, app launching and HDMI switching
  on your own TV.
- **Choose Settings → Theme → Dark** for the full Cyberpunk HUD look.
- **Pin only what you use.** A short dock is faster to navigate with a
  remote than a full one; hide the rest with long-press OK.
- **Set the app-grid columns for your viewing distance.** Fewer columns
  give larger tiles.
- **Turn motion down on slow hardware.** The launcher follows the
  reduced-motion setting.
- **Use HTTPS addresses for website tiles.** Only HTTPS addresses are
  accepted.
- **Leave weather off unless you want it.** It needs a location you type in
  and makes network requests only when enabled.

## Works well with

**Graphic design.** The wallpaper and theme settings make the TV a branded
surface: design a wallpaper at the TV's resolution in Photoshop, Affinity
or Figma and set it in Settings.

**Product visualization.** A website tile opens any HTTPS page full screen,
so a web-based 3D product viewer, a turntable page or a portfolio site
becomes a one-press showroom or studio display.

**Film.** Use a website tile for a review or dailies page, or pin a media
player app, to turn a TV into a quick screening monitor for a team.

**Automation.** The `scripts/` folder has emulator smoke, upgrade and
release checks in Python, and the GitHub Actions workflows build and check
every change. The TV Hub contract in
[docs/TV_HUB_INTERFACE.md](docs/TV_HUB_INTERFACE.md) defines how a separate
provider app can plug in.

## Modules

The six Android libraries own launcher UI, theme configuration, installed-app discovery, read-only diagnostics, update logic and the TV Hub contract. The application composes them and owns the Android installer integration. See [architecture](docs/ARCHITECTURE.md) for dependency rules. The `home` preference file, `config` key and schema1 retain compatibility with earlier development builds.

## Release preparation

The development version is 0.4.0/code 4. SHA-pinned GitHub Actions workflows build and check the application, then stage unsigned release input. The local release script aligns and signs the reviewed APK, verifies its identity and generates an HTTPS update manifest and checksums. Python 3.11 or newer is required for the release script. The signing key is managed offline by the release owner. Read [release signing](docs/RELEASE_SIGNING.md) and [installation and recovery](docs/INSTALLATION_AND_RECOVERY.md) before provisioning a production identity or feed.

## Recovery and approval boundaries

Keep `com.google.android.tvlauncher` installed and enabled. The owner chose to keep the stock launcher as default. The earlier 0.3.0 production app and OwnTV were installed as ordinary apps; the owner reported remote navigation, application launching and HDMI switching working. Version 0.4.0 requires its own physical acceptance. Default-HOME changes always need separate explicit approval. Before making any such change, test the stock-launcher and Settings recovery routes recorded in the Phase1 report.

The existing Phase1 state snapshot remains outside this project. No reset, broad restore script, firmware image or system package modification is needed to test this launcher as an ordinary app.
