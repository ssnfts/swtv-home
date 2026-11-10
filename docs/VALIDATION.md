# Phase 3 validation

Validation ran 10–11 November 2026. Development build `app.swtv.home` is version 0.2.0 (code 2), with minimum API 29 and target/compile SDK 35. Physical TV deployment is a separate phase.

## Evidence and scope

Build and test counts, lint issues, APK/signature metadata and the exact source revision are recorded in the adjacent `SWTV-Phase3` handoff folder. `test-summary.json`, `apk-verification.json` and `source-verification.json` are the authoritative artifact records. Build logs, JUnit XML, emulator screenshots/hierarchies and read-only code-review reports accompany them.

Six independently compiled Android libraries implement theme configuration, app discovery, reusable launcher UI, read-only diagnostics, unconfigured update state and TV Hub navigation. The architecture guard checks permitted edges/no cycles, library-to-app isolation and unique AppEntry ownership. Every library is assembled to an AAR by the all-module build; the renderer's two library dependencies are explicit. The pinned JDK17/Gradle8.9/AGP8.7.3/Kotlin2.0.21 toolchain is unchanged.

All 47 JVM tests and four API 29 instrumentation tests passed. Lint found zero errors and six existing app warnings: legacy receiver flag analysis, vector rasterization, two test dependency update notices, the fixed landscape orientation and the data extraction configuration recommendation. Library lint reported no issues.

## Upgrade preservation

The guarded upgrade harness first verified `ro.kernel.qemu=1` and API29, then captured the existing0.1.0/code1 app's `home`/`config` JSON, installed0.2.0/code2 using the same package/signer and started the Activity without clearing data. Every saved schema1 value and type matched afterward. Raw before/after XML/JSON and the result are in `upgrade-validation`.

This actual upgrade used the extracted Task1 build. Subsequent Phase3 builds retain the same code2/package, configuration codec and signer. The legacy fixture test additionally covers all configuration fields, including modules, hidden apps, weather opt-in, wallpaper and the IPTV override. This development update is not evidence for forced downgrades or production rollback.

## Emulator safety and platform coverage

The workspace-owned emulator is Google Android TV API29 x86,1920×1080/320dpi,1536MiB RAM, two virtual CPU cores, WHPX and software graphics. Test scripts reject non-emulator serial strings before running ADB and verify emulator/API identity before installation or input injection. No unscoped connected-device Gradle task was used. The physical TV was not targeted.

Instrumentation inspects actual Android input and TV Hub intents. All 16 extended UI smoke checks passed on the delivered APK. They cover home and D-pad focus, the absent OwnTV and reserved Hub states, settings persistence after process recreation, app context actions, dynamic build identity, unconfigured updates and on-demand diagnostics. Some dialogs use targeted taps. A stationary 900 ms touch hold exercises long-click. Held remote OK still needs a physical TV test.

The recorded HOME resolver stays `com.google.android.tvlauncher/.MainActivity`. No preferred-HOME or system-setting command was issued. There is no installed TV Hub provider or OwnTV player in this emulator; compatibility policies are unit-tested, not an end-to-end provider/playback acceptance test.

## Artifact checks

The delivery script inspects package/version/API, permits only `android.permission.INTERNET`, rejects packaged JNI libraries and verifies the debug APK signature. The development signer is compared with the preserved0.1.0 APK. Source archive entries and bytes are checked against the clean committed checkout; SDKs, build caches, APKs and keystores are excluded.

## Limits and deferred work

The final software emulator sample recorded 91 janky frames out of 103, with a median frame time of 26 ms. One cold start took 1,976 ms, and the post-smoke process used 29,945 KiB PSS. Physical 32-bit ARM installation, cold start, frame pacing, memory pressure, full remote navigation, wallpaper providers and HDMI/CEC switching remain later acceptance work.

The Phase2 minor wallpaper limitation remains: a failed decode is cached for that URI until selecting a different wallpaper or recreating the Activity. Live weather/offline behavior and an installed OwnTV application were not exercised. Diagnostics report filesystem availability, not total physical storage/chipset identity; unsupported values remain unavailable.

Production signing, GitHub Actions/release publishing, HTTPS manifests, APK integrity/install flows, release migration/rollback tests and all media/playlist/EPG capabilities remain deferred. The original launcher is retained. Network debugging remains as it was after discovery; its requested shutdown belongs after later approved deployment/recovery validation.

## Repeat the checks

Run `build-local.ps1` in the prepared workspace, or the README Gradle command in Android Studio's configured JDK/SDK environment. Run the architecture guard. Build `:app:assembleDebugAndroidTest`, then use `scripts/emulator-instrumentation.py` with explicit app/test APK paths and a verifiedAPI29 emulator serial. Do not use an unscoped `connectedDebugAndroidTest` task while a physical device may be attached.

```powershell
python scripts/emulator-smoke.py --adb PATH_TO_ADB --serial emulator-5556 --apk app/build/outputs/apk/debug/app-debug.apk --out PATH_TO_OUTPUT --extended --phase3
```

For upgrade validation, `scripts/emulator-upgrade.py` requires an existing code1 app and real saved config; it refuses another version, downgrade or invented baseline. Do not reset the current code2 emulator to rerun it. Use a separate prepared emulator if repeating that scenario.
