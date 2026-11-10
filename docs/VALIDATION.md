# Phase 2 validation

Tested on 10 November 2026. This is an initial development build, not a production or physical-TV acceptance sign-off.

## Build and artifact

- Application `app.swtv.home`, version0.1.0 / code1; minimum API29, target/compile35.
- JDK17, Gradle8.9, AGP8.7.3, Kotlin2.0.21; build, unit tests and `lintDebug` completed successfully.
- 27 JVM tests passed with zero failures/errors: core10, runtime policies4, review regressions4, interaction regressions3, dock/input regressions3 and wallpaper-work regressions3. One additional Android platform test passed on the API29 TV emulator, asserting ACTION_VIEW, the exact encoded HW5 passthrough URI and explicit channel MIME. It first failed against the old untyped intent.
- Lint: zero errors, six warnings. Warnings cover fixed TV landscape, vector-banner size, API31+ data-extraction configuration recommendation, and lint's inability to inspect the protected-broadcast filter through a helper in the guarded legacy registration path. Two further notices recommend newer versions of the intentionally pinned, test-only AndroidX runner/JUnit libraries. No broad lint baseline or blanket suppression was used.
- `aapt2` confirmed package, minimum/target SDK and only `android.permission.INTERNET`.
- No native `.so` libraries are packaged. The APK is DEX/platform-based; a32-bit ARM hardware install remains untested.
- `apksigner verify --verbose --print-certs` passed; the development APK uses Android's debug certificate and APK Signature Scheme v2. This is not the Phase4 production signing identity.
- APK bytes:2,558,819. SHA-256: `9593ce55ed7b6f5047a84b97654286e39f0392ed97af4a84c82ccade2a51daf1`.

Build evidence refers to application code through commit `06224f0`. Later documentation/test-harness commits do not change the APK.

## Emulator checks

Google Android TV API29 x86 image,1920×1080 at320dpi,1536MiB guest RAM, two virtual CPU cores, existing Windows Hypervisor Platform, software graphics. The harness refuses non-emulator serials and verifies `ro.kernel.qemu=1` before installing or injecting input.

Passed checks:

1. Install the debug APK as an ordinary emulator app and explicitly open its activity.
2. Render the home controls; move actual D-pad focus between controls and down through rows.
3. Open the absent-OwnTV dialog and verify its project-releases path; keep TV Hub as a separate future-app entry.
4. Open settings, change theme and grid columns, and verify saved JSON after process recreation.
5. Open an app's long-click menu and pin an app to the dock.
6. Observe no launcher fatal exception in the captured emulator log window.

Targeted taps entered some dialogs. A stationary900ms touch hold exercised the native long-click action: API29's shell `keyevent --longpress` behaved as a short click in this emulator, and emulator-console key injection did not reproduce a held remote OK. **Actual held-OK remote behavior is not validated.** These tests are not comprehensive traversal or accessibility tests.

The emulator HOME resolver remained the stock Leanback launcher in the recorded check; no preferred-HOME command was issued. Physical-TV behavior can differ.

## Performance observations and limits

One final debug cold-start sample reported2437ms; one post-smoke `dumpsys meminfo` snapshot reported37,494KiB total PSS (approximately36.6MiB). These are emulator observations, not physical-TV performance figures.

Software-emulator graphics were poor:101 recorded frames,86 janky frames(85.15%),28ms median. Test automation and a software renderer affect these samples, but the result does **not** demonstrate smooth animation or a60fps target. Physical startup, frame pacing and memory-pressure validation remain required in the later device-validation phase. No performance improvement over the stock TV launcher is claimed.

## Review-driven corrections

The review process corrected foreground package-change refresh, cache identity across city changes/cancellation, bounded/deduplicated icon work with weak generation-safe bindings, local-time automatic theming and scheduled boundaries, focus-border clipping, same-city weather request coalescing across minute checks, and stable picker snapshots across catalog changes. Final review also corrected canonical dock editing after removals, occupied-slot replacement/swap, explicit input MIME, current passthrough membership/state checks and bounded/coalesced wallpaper work.

Regression tests ran against compileable prior behavior and failed for the relevant cache/theme/queue and controller-interaction cases before the corrections passed. Task reviews after the corrections returned spec PASS and code-quality APPROVE.

## Unvalidated or deferred

- Physical SWTV installation, remote controls/held OK, HDMI/CEC switching and default HOME selection.
- Running an installed OwnTV APK or streaming; only the shortcut's absent-app path was exercised. No IPTV APK was downloaded or installed.
- Live weather responses and offline transitions on the physical TV; provider behavior is guarded in code but not established by the UI smoke.
- Wallpaper selection through this OEM's document provider and memory pressure from user images.
- Production release signing, hosted update manifest, APK verification/install pipeline and rollback/migration release tests: Phase4.
- Independent Gradle modules and formal TV Hub integration contract: Phase3. Playback/playlist/EPG functionality is absent.

Network debugging remains enabled from discovery; shutdown is the requested post-deployment action and was not performed during development. The physical TV was not targeted by these emulator tests.

## Repeat the checks

From the project root, run the documented Gradle build, then:

```powershell
python scripts/emulator-smoke.py --adb PATH_TO_ADB --serial emulator-5556 --apk app/build/outputs/apk/debug/app-debug.apk --out PATH_TO_TEST_OUTPUT --extended
```

The emulator must already be an API29 TV image. No physical serial is accepted. Raw logs, screenshots, XML hierarchies, APK metadata/signature and structured results are provided in the separate Phase2 validation output folder.

## Android platform test

The test-only AndroidX runner1.6.2 / JUnit extension1.2.1 are separate from runtime dependencies. Build with `:app:assembleDebugAndroidTest`; run `scripts/emulator-instrumentation.py` with application/test APK paths and an API29 emulator serial. Do not use an unscoped connected-device test task while the physical TV is attached. The guarded script verifies emulator identity before installation.

