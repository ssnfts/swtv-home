# Phase 4 validation

This is the historical 0.3.0 development record. Subsequent production provisioning and physical acceptance are recorded in the delivery reports. See [0.4.0 Cyberpunk and website validation](VALIDATION_0_4.md) for the current development change; older unprovisioned/pending statements below describe the earlier test date.

Validated locally on 11 November 2026. Development version 0.3.0/code 3 targets Android API 29 and newer, with compile/target SDK 35. Final application source is commit `a1015dcd1733a29a84f70ff44bac934b42c8e298`. Later documentation and test-harness commits are recorded in the delivered source archive metadata.

## Build and security checks

- All 78 JVM tests passed, with zero failures, errors or skips. Four Python release-tool tests passed. The six libraries retain their approved dependency graph and compile as Android AARs.
- Debug and release assembly, app instrumentation assembly and lint completed successfully. App lint reported zero errors, 14 debug warnings and 12 release warnings. These cover inherited receiver/vector/dependency/platform notices and English UI string formatting. Detailed per-module XML is in the delivery evidence.
- The delivered debug APK is `app.swtv.home`, version 0.3.0/code 3, min API 29, target SDK 35, with only INTERNET permission and no native libraries. Its development signer matches the preserved 0.2.0 build. Release-only metadata, install permission and private result receiver were inspected from the actual release APK.
- The source ZIP is checked file by file against the clean committed project. Keystores, passwords, test APK fixtures, SDKs and build caches are excluded. The disposable emulator signing identity stays in private scratch storage and is distinct from a production identity.

The network client passed real loopback TLS regressions. A blocked response-header request hit its two-second test deadline in 2.014 seconds. A blocked download cancelled and removed its partial file in the 0.047-second test. A Basic authentication challenge triggered one request, no JVM authenticator and no ambient cookie or authorization header. A 503 Retry-After response triggered one request. Production policy retains one 120-second budget, checked origins, manual redirects and bounded streamed content.

## Debug upgrade and launcher behavior

A dedicated Google Android TV API 29 x86 emulator preserved its app settings across a normal 0.2.0/code 2 to 0.3.0/code 3 debug upgrade. That transition used the initial reviewed Phase 4 build. The later Android 10 archive compatibility fix changed only archive certificate collection and added an opt-in test. Installing the final code-3 debug APK over the earlier code-3 build also retained the existing preferences.

On the final debug APK, all four existing Android intent tests passed. Four opted-in fixture methods were skipped by the ordinary test invocation. Comparing the stored preference bytes before and after that invocation confirmed it made no fixture changes. The runner reports eight discovered methods, including those four skips.

All 16 launcher smoke checks passed on the delivered APK. They covered startup, home controls, D-pad focus and row movement, separate OwnTV and TV Hub missing states, theme/grid persistence, process recreation, app long-click menu, current version, unconfigured update status and public diagnostics. Some dialog entry used targeted taps. A stationary 900 ms touch hold exercised long-click because the API 29 shell did not reproduce held remote OK.

The final software-emulator sample recorded a 579 ms cold start, 39,334 KiB process PSS, and 41 janky frames among 113, with a 10 ms median. This sample depends on software graphics and host load. Physical startup, memory pressure and frame pacing remain acceptance work.

## Android 10 archive regression

The first real signed-flow test rejected a correct fixture before installation. A diagnostic proved its bytes, package, version and minSdk matched, while Android 10 returned null archive signingInfo for GET_SIGNING_CERTIFICATES alone. The Android 10 framework calls certificate collection only when GET_SIGNATURES is requested. The adapter now requests both flags for collection and continues to trust only current `signingInfo.apkContentsSigners`. It never falls back to legacy signatures or signing history. The retained opt-in diagnostic failed before the fix and passed on the final target. [Android 10 PackageManager source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/core/java/android/content/pm/PackageManager.java)

## Signed update and recovery acceptance

A second, isolated API 29 Android TV emulator used non-debuggable release APKs signed with a disposable test certificate. The update driver loaded controlled APK and manifest fixtures from the test APK. It used the production identity reader, engine, update panel, staging and PackageInstaller path. The host pressed the actual app and Android controls.

The recorded nine acceptance checks passed.

1. Install the code-3 signed baseline and explicitly seed every relevant schema-1 setting.
2. Verify that installation cannot advance before source permission is granted.
3. Open Android source Settings from the app and allow the SWTV Home row through its switch.
4. Reach Android's installation prompt, select Cancel, and verify code 3 and all seeded settings remain.
5. Repeat the update flow, select Android Install, and verify code 4 is installed.
6. Run the independent preference assertion after the update.
7. Apply a same-signer code-5 recovery APK through the same confirmation flow.
8. Verify every setting after recovery.
9. Confirm HOME still resolves to `com.google.android.tvlauncher/.MainActivity`.

The recovery fixture was built from the preserved Phase 3 source at `d5c7ede`, with only version name/code changed to 0.3.2/code 5. It demonstrates a real return to earlier code with a higher version and retained preferences. It intentionally predates the update UI. A production recovery should select a known-good release source and retain the intended distribution configuration.

Successful self-updates terminate the instrumentation target process. The host validates installed version and preferences independently afterward. Explicit source-permission and Android-confirmation screenshots, transcripts, XML and structured results accompany the handoff. The physical TV was outside these tests.

## Offline release infrastructure

Both workflows passed local YAML, full-SHA action pin, read-only permission and shell syntax checks. The Python packager passed malformed identity/permission, metadata namespace and duplicate, wrong/multiple signer, and tampered-input checks. The SDK-backed negative case rejected an APK lacking the release installation configuration before signing. Positive signing with the disposable key exercised actual zipalign, apksigner, certificate inspection, embedded feed/certificate checks, and manifest/checksum generation from signed bytes.

Hosted GitHub Actions, a public release repository, a live feed and the owner's production signing identity remain to be provisioned. No remote workflow run or production publication is claimed. Network transport and the Android installer were tested separately with controlled TLS and asset fixtures.

## Repeat and limits

Build with the README commands and run `python -m unittest discover -s scripts/tests -v`. The guarded `emulator-upgrade.py` accepts `--from-code 2 --to-code 3` for an existing saved baseline. It requires increasing codes and never resets data. Use `emulator-smoke.py --extended --check-status --expected-version 0.3.0` on a verified API 29 emulator.

The signed harness `scripts/emulator-release-check.py` requires a fresh task AVD named `SWTV_PHASE4_SIGNED_API29`, signed baseline and test APK paths. Build the release instrumentation with `-PswtvTestBuildType=release` and a test-only asset directory containing update.json/update.apk and recovery.json/recovery.apk. Sign the baseline, candidates and instrumentation with one disposable certificate. The host harness refuses physical serials, seeds fixtures only through explicit test opt-in, and drives the native UI. Do not use an unscoped connected-device Gradle task while a physical device may be attached.

Physical 32-bit ARM installation, remote held OK, HDMI/CEC, OEM wallpaper providers and live weather remain unverified. The carried wallpaper limitation persists: a transient failed decode stays cached for that URI until selecting another image or recreating the Activity. Production key backup, repository publication and physical recovery acceptance require their owner-managed steps. Network debugging shutdown remains the requested step after later approved physical deployment.
