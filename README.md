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

The dedicated **OwnTV** tile defaults to package `tv.own.owntv`, verified from [the requested GitHub project's build configuration](https://github.com/Thedurancode/owntv/blob/main/app/build.gradle.kts). If OwnTV is absent, choose another installed app or explicitly open [OwnTV releases](https://github.com/Thedurancode/owntv/releases) in a browser. The launcher does not download or install OwnTV. Choose a compatible32-bit ARM release only after verifying its source and compatibility in the later provisioning phase. Package lookup and an ordinary launch shortcut do not embed any OwnTV code.

The separate **TV Hub** tile reserves `app.swtv.tvhub` for future development through a versioned, explicit navigation contract. See [the provider interface](docs/TV_HUB_INTERFACE.md). There is no TV Hub APK, playlist ingestion, EPG synchronization or streaming/playback implementation in this launcher.

HDMI/TV shortcuts enumerate device TV inputs and use the platform's typed channel intent. OEM permission and remote behavior require validation on the actual TV before claiming input switching works there. Unavailable targets produce an explanatory dialog instead of altering firmware or app state.

## Privacy and settings

Configuration is app-local. Weather is off by default and requires an explicit user-entered location and opt-in. Enabled weather uses Open-Meteo HTTPS endpoints; no device location permission is requested. Clock/date follow the TV's locale/time zone. Weather attribution is displayed in the app.

The Diagnostics control collects an on-demand snapshot of public device/build, display, memory and app-filesystem information. It does not collect serial numbers, accounts or private files, or change system settings. About reports the actual application version and an unconfigured release-update provider. GitHub Actions, hosted manifests, secure release signing and approved APK installation are Phase4 work.

No accessibility remapper, root access, boot receiver, persistent background service, system settings writes or package-disable functionality belongs to this project. System Settings and browser links open only after user action.

## Modules

Version0.2.0 separates launcher UI, theme configuration, installed-app discovery, read-only device diagnostics, update state and the TV Hub contract into six Android libraries. The application is their composition root. See [architecture](docs/ARCHITECTURE.md) for ownership and dependency rules. The `home` preference file, `config` key and schema1 remain compatible with0.1.0.

## Recovery and approval boundaries

Keep `com.google.android.tvlauncher` installed and enabled. Physical-TV installation, real-remote/HDMI tests, default-HOME selection and network-debugging shutdown have not been authorized as part of this development step. Default-HOME changes always need a separate explicit approval. Before making any such change, test the stock-launcher and Settings recovery routes recorded in the Phase1 report.

The existing Phase1 state snapshot remains outside this project. No reset, broad restore script, firmware image or system package modification is needed to test this launcher as an ordinary app.
