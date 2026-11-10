# SWTV Phase 4 updates implementation plan

> For agentic workers: execute with subagent-driven-development. Steps use checkboxes for tracking.

**Goal:** Build a verifiable, user-approved Android TV launcher update flow and offline signed release pipeline.

**Architecture:** The update-manager library owns manifest, origin, transport, APK policy and bounded staging. The app owns lifecycle, UI, Android package inspection and user-approved PackageInstaller sessions. GitHub Actions builds unsigned artifacts and a separate local script signs and stages reviewed releases.

**Tech stack:** Kotlin 2.0.21, JVM/JDK 17, AGP 8.7.3, Gradle 8.9, Android platform, GitHub Actions, Python 3 and Android Build Tools.

## Global constraints

- Preserve app ID `app.swtv.home`, min API 29, compile/target 35, six-module boundaries, OwnTV, TV inputs, stock HOME recovery and `home` / `config` schema 1.
- Default version 0.3.0/code 3, debug signer and app ID unchanged. Release uses a separate owner-managed signer and explicit trust configuration. No production key, authentication token or credentials in Git, CI or outputs.
- No physical TV operations, HOME changes, publication, Git remote creation, streaming, Hub APK or production key creation. Actual package tests target only the task-owned API 29 emulator.
- Release-only install permission and non-exported result receiver. Both install and permission-settings navigation require explicit UI action. Android owns confirmation. No automatic update/check/install.
- Preserve Phase 3 artifacts. Record live CI/hosting as unverified unless actually run with a separately approved destination.

## Task 1 — Update trust, transport and staging

Files: `update-manager/src/main/java/app/swtv/updates/{UpdateManifest,UpdateOrigin,UpdateTransport,UpdateEngine}.kt` and focused unit tests. No app source/build edits. Approved design and exact interface handoff in work/phase4/task1-brief.md.

- [ ] Write behavioral tests for exact schema/types, canonical URL trust and redirect policy, stale/API/package/signer rejection, real byte/hash/size mismatch, bounded streaming/cancellation, and temp ownership. Record meaningful failure against preimplementation behavior.
- [ ] Add strict manifest parser and GitHub feed/asset trust, public HTTPS transport with per-operation deadline, length limits and redirect validation, and typed outcomes. Implement verified private staging and revalidation before install. Trust the pinned installed and archive certificate; PackageInstaller verifies actual signature.
- [ ] Run `build-local.ps1` with `:update-manager:testDebugUnitTest`, `:update-manager:lintDebug`, and `:update-manager:assembleDebug`, plus architecture guard. Commit the implementation and record its API and tests. Review before app wiring.

## Task 2 — App, installer and runtime validation hooks

Files: `app/build.gradle.kts`, app main Kotlin under `app.swtv.home.updates`, `MainActivity.kt`, release manifest and isolated release Android instrumentation. Keep the launcher-ui and other libraries unchanged.

- [ ] Define build-time release feed and signer, release-only permission/receiver, default debug and version, explicit test-only version overrides. Preserve unconfigured About default for debug builds. Build unsigned non-debuggable release.
- [ ] Wire Android installed/archive metadata and foreground update dialogs. Use one bounded job and cancel work/cleanup on lifecycle transitions. Check, Download, Install and permission actions remain separate and require intent from the user.
- [ ] Stage only the verified, rechecked newer launcher APK. Use one PackageInstaller session, explicit scoped result PendingIntent/receiver, user action requirement where available, foreground-only confirmation, and cleanup on cancellation and failure. Never take an untrusted intent or APK path from an exported component.
- [ ] Provide API 29 test tooling through a release test APK that supplies controlled signed APK fixtures through the same production verification and installer path. Test-only injection stays in androidTest. Build debug/release + tests, run lint and guard, commit, report and review.

## Task 3 — GitHub Actions and offline release tooling

Files: `.github/workflows`, `scripts/prepare-release.py`, `docs/RELEASE_SIGNING.md`, `docs/INSTALLATION_AND_RECOVERY.md`, `docs/examples/swtv-home-update-v1.json` and README.

- [ ] Add SHA-pinned, read-only CI and manually dispatched release workflows. Run Gradle tests/lint/assembly, guard and record artifact checksums/revision. Use verified upstream action SHAs. No automatic publishing, CI secrets or untrusted pull-request execution with write credentials.
- [ ] Implement offline zipalign/apksigner preparation with interactive password handling and certificate, package, version/API checks. Build strict manifest and checksums from exact signed bytes. Reject invalid paths/trust config, debug inputs, incorrect identity and prior/equal release codes when provided. Docs give encrypted key/backup custody and explicit promotion procedure.
- [ ] Validate workflow syntax and release packaging with disposable local keys. Document initial install and same-signer higher-code recovery. Commit and review.

## Task 4 — Emulator integration and delivery

- [ ] Use a verified API 29 emulator only. Verify debug Phase 3 -> Phase 4 configuration preservation. Use offline-signed local release fixtures with the same disposable cert for baseline, update and recovery, then verify system prompt/cancel/confirm and configuration retention. Keep installed stock HOME unchanged. Capture failure evidence if platform policy limits a check.
- [ ] Check all Gradle builds/tests, lint, manifest permissions per variant, checksum/cert/package binding, archive contents and source links. Run final independent review. Export Phase 4 development APK, source ZIP, release tooling, example manifest and evidence without credentials. Preserve earlier outputs. Stop only the task emulator.
