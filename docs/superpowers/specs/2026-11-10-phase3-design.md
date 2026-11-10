# SWTV Home — Phase 3 design proposal

Date: 10 November 2026. Approved by the user. Scope: modular architecture and the TV Hub integration contract, continuing the completed Phase 2 development build. Physical TV deployment remains deferred.

## Recommended approach and alternatives

Use six Android library modules with the existing `app` application as their composition root. Each library can compile/test without the application, and dependencies point one way. This provides real Gradle boundaries without introducing dynamic-feature delivery or a dependency-injection framework.

Alternatives considered: keep package-only boundaries (less build configuration, but does not meet the requested independent-module goal), or dynamic feature modules (independent delivery machinery adds complexity that is unnecessary for the current small launcher). Recommend ordinary libraries.

## Module ownership

| Module | Owns | Dependencies |
| --- | --- | --- |
| `:theme-config` | Configuration models/codec, app-private preference adapter, theme and effect policies | Android/platform and Kotlin only |
| `:app-discovery` | App models, catalog/dock/picker rules, package launch discovery, current TV-input policy and typed input intents | Android/platform and Kotlin only |
| `:launcher-ui` | Native home surfaces, settings controller, focus rules, bounded icons/wallpaper, optional weather widgets | theme-config and app-discovery |
| `:device-diagnostics` | Read-only SDK/build/ABI/display/memory/storage snapshots and formatting | Android/platform and Kotlin only |
| `:update-manager` | Build identity, typed update states and an explicitly unconfigured provider | Android/platform and Kotlin only |
| `:tv-hub-contract` | Versioned navigation contract, compatibility validation and explicit-package launch client | Android/platform and Kotlin only |
| `:app` | Activity/lifecycle assembly, resources/manifest and binding callbacks to the libraries | All six libraries |

No library may import the application module or `MainActivity`. The UI settings controller receives a small host interface rather than depending on the Activity implementation. Libraries use their own package namespaces. Test policies remain with their owning modules.

All modules retain minimum API 29, compile SDK 35 and JVM 17 compatibility. The existing pinned build toolchain and AndroidX test dependencies remain unchanged. No native library or media dependency is added.

## Preserve existing behavior and data

Keep application ID `app.swtv.home`, current preference file/key names and JSON schema compatible with the Phase 2 build. Dock order, themes, hidden apps, module tiles, weather opt-in/location, wallpaper URI and IPTV target must survive an ordinary app update. Existing `tv.own.owntv` default, user-triggered GitHub releases link, input eligibility/MIME handling, bounded work and foreground lifecycle protections remain intact.

The next development APK is version 0.2.0 / code 2, debug-signed with the same local development identity. Production signing and distribution remain Phase 4 work. Preserve the Phase 2 APK/source archive as an earlier checkpoint.

## Device diagnostics

Provide a user-opened diagnostics view with public build/manufacturer/model/board/ABI/API fields, display dimensions/density, OS memory information, this process's memory and app-filesystem free space. Collect on demand and handle unavailable fields. Do not collect serials, account data, private application files or identifiers, invoke ADB, grant privileges, or change settings.

## Update-manager boundary

Define a typed provider interface and build/version model, with an `Unconfigured` result used by the launcher. The About/Updates view shows installed version and clearly reports that release updates are not configured. No manifest polling, APK download/installation, signing-key creation, GitHub publishing or automatic update behavior is introduced here.

## TV Hub contract version 1

OwnTV remains independent. This protocol applies only to the future app `app.swtv.tvhub`.

- Navigation action: `app.swtv.tvhub.action.OPEN_HUB`, explicitly restricted to package `app.swtv.tvhub`.
- Required provider manifest metadata: `app.swtv.tvhub.contractMajor` integer 1.
- Request fields: contract major 1 and destination `home`. No playlist, stream, account, credential or file URI is passed.
- Resolve only an enabled, exported handler in the expected package. Reject absent, malformed or incompatible metadata before starting an activity; catch launch failures and distinguish missing/incompatible/unavailable states in the launcher.
- Allow compatible minor additions while rejecting unsupported major versions. Do not use an implicit fallback that silently bypasses version checks.
- Navigation carries no privileged or sensitive data. A package name alone is not a publisher-identity guarantee; any future credential-bearing IPC needs a separately reviewed signing/permission policy.

Document the future provider's manifest/action/metadata requirements, compatibility cases and migration rules. Reserve M3U ingestion, XMLTV synchronization, channel matching, Media3 playback, caching and favorites as future capabilities only. Do not add a player, EPG parser, playlist processing or a separate installed TV Hub APK in this phase.

## Verification and deliverables

Move/split the existing 27 JVM tests into their owning modules without losing coverage. Add tests for persisted Phase 2 configuration compatibility, module dependency rules, diagnostic formatting/unavailable values, update-state behavior and TV Hub version/request validation. Retain the actual API 29 typed-input instrumentation test and emulator-only guards.

Build each library independently, run module unit tests and lint, assemble the app/test APKs, and exercise the existing emulator smoke plus diagnostics/update/TV Hub missing-state checks. No all-connected-device test task is allowed while the physical TV may be attached. Do not claim real TV/remote/HDMI/performance validation.

Deliver updated Android Studio source, a module dependency/ownership document, the TV Hub interface specification, a 0.2.0 development APK and source archive, checksums and validation notes. No physical TV installation, preferred-HOME selection, package enable/disable, firmware action or debugging shutdown is included.

References: [Android modularization patterns](https://developer.android.com/topic/modularization/patterns), [explicit intent behavior](https://developer.android.com/guide/components/intents-filters), [manifest metadata](https://developer.android.com/guide/topics/manifest/meta-data-element).

