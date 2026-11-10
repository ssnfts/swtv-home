# SWTV Custom Home Phase 4 design

Approved on 11 November 2026.

The next development build is 0.3.0, version code 3. It adds release tooling and a user-initiated self-update flow to the existing Android TV 10 launcher. The application ID remains `app.swtv.home`, with minimum API 29, compile and target SDK 35, and the current JDK, Gradle, AGP and Kotlin versions. Configuration remains in `home` / `config`, JSON schema 1.

## Signing and release approach

| Approach | Trade-off |
| --- | --- |
| **Offline production signing (recommended)** | GitHub Actions builds and tests the exact source revision and produces an unsigned release APK. The release owner aligns or checks alignment, signs locally, verifies the output and prepares the release bundle. The private key stays outside GitHub and the project. |
| Protected GitHub Actions signing | A protected release workflow can sign and publish with fewer manual steps. It also exposes the production key to the runner and increases the impact of a compromised workflow or dependency. |
| Manual APK delivery over ADB | Useful for recovery and for initial provisioning. It leaves the user without the requested on-TV update flow. |

The production identity uses a dedicated signing certificate. The existing debug APKs keep their development signer. Android checks signing identity for in-place updates, so a production APK cannot replace the same-package debug APK. The physical TV has never received an SWTV APK, so start its eventual installation with the production identity. Validate debug upgrades separately in the emulator. [Android signing documentation](https://developer.android.com/studio/publish/app-signing)

## GitHub builds and offline release packaging

- Add GitHub Actions workflows for pull request checks and a deliberate release build from a reviewed revision. Run module boundaries, JVM tests, lint and assembly on the pinned toolchain. Pin actions to verified full commit SHAs and grant the build jobs read-only repository access. Archive unsigned release output with the source revision and checksums. [GitHub Actions security reference](https://docs.github.com/en/actions/reference/security/secure-use)
- Provide a local release script and runbook. The owner keeps the production keystore on encrypted storage, with an encrypted offline backup and a separate password recovery record. Android SDK `apksigner` prompts for the password. The script checks the signed APK, its certificate fingerprint, its package and version, and generates the manifest from the final APK bytes. Verify that a backup can reproduce the public certificate before relying on it. [Android APK signing tool](https://developer.android.com/tools/apksigner)
- Keep a separate version code sequence and asset names for each application. Phase 4 implements the launcher release only. The future TV Hub can use its own package, version sequence, manifest and feed without being compared to launcher versions.
- Prepare a release bundle with signed APK, versioned manifest and checksums for owner review. Use a dedicated GitHub Releases feed for the launcher when a distribution destination is chosen. A local-only source checkout and an unconfigured feed are supported. This phase can be built and tested locally before a GitHub repository or production key is provisioned. Production signing and publication require the owner's key and an approved destination. [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)

## Update manifest and verification

A release build accepts a public, build-time feed URL and APK certificate SHA-256 fingerprint. Feed configuration is optional. An ordinary debug build and a release with no valid feed configuration show “Release updates are not configured.” The app uses a public HTTPS download path and needs no GitHub token. Start with one dedicated launcher feed, whose stable manifest asset is `swtv-home-update-v1.json`. Its APK URL points to a specific release asset rather than a moving latest link.

Manifest schema 1 contains `schema`, `packageName`, `versionCode`, `versionName`, `minSdk`, `apkUrl`, `apkBytes` and `apkSha256`. `packageName` must be `app.swtv.home`, version code must be strictly newer than installed, and `minSdk` must be supported. The script and app validate exact field types, byte count and SHA-256 format. The file is capped at 64 KiB and the APK at 32 MiB. Download streams to app-private temporary storage with connection and read timeouts, an overall deadline and a limited number of redirects. Accept only HTTPS and the configured release origin and expected GitHub asset redirect hosts. Reject malformed URLs, unexpected hosts, unsupported versions, wrong sizes and wrong hashes. Temporary files and abandoned sessions are cleaned up.

Before offering installation, compare the APK's archive metadata with the manifest and the expected package and certificate. Also require the installed signer to match the configured release certificate. SHA-256 confirms the bytes match the manifest. The release script verifies the signature with `apksigner`, and Android's installer enforces signing identity and verifies the APK at install time. HTTPS protects the manifest in transit. A compromised feed could withhold updates or offer an older genuine release to a device that has yet to install it. The version and signing checks still apply. This design uses the APK signing key as the install trust anchor and does not introduce a separate manifest key.

## TV update flow

From About, the user chooses **Check for updates**. If an eligible release is found, show the version, download size and a download action. After verification, show **Install update**. Only that action prepares an Android `PackageInstaller` session for `app.swtv.home`. Installation confirmation is handled by Android. If Android requires permission to install from this source, explain it and let the user open the system screen. Recheck permission and require the user to resume installation. Debug builds retain their existing permission set. The release installer uses `REQUEST_INSTALL_PACKAGES`, and uses `USER_ACTION_REQUIRED` where available. [PackageInstaller session parameters](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams), [unknown source checks](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())

Checking, downloading and staging run off the main thread, with one active operation and cancellation. Installer results are tied to the current session through an explicit, non-exported receiver. A system confirmation prompt launches only while the user is in the update flow. Cancellation, permission refusal, insufficient storage, network failure and invalid artifacts leave the current launcher usable. Update status shows the installed version after restart. [PackageInstaller result codes](https://developer.android.com/reference/android/content/pm/PackageInstaller#STATUS_PENDING_USER_ACTION)

## Migration, recovery and acceptance

- Test invalid manifest fields and origins, tampered or oversized downloads, wrong signer or package, stale version codes, incompatible SDK, cancellation and update state transitions. Exercise the real installer flow and confirmation on the API 29 emulator using local test-signed builds. Use disposable test keys and controlled fixtures for these checks. Report external CI and hosting verification separately from local verification.
- Check that an in-place update preserves dock, themes, hidden apps, wallpaper reference, weather opt-in and the OwnTV target. Exercise a recovery build made from known-good code, signed with the same test key and a higher version code. Keep schema 1 compatible in both directions across these test builds.
- Document first production installation, verifying certificates and checksums, user consent, and recovery. Recovery uses a new higher-version APK from known-good code and the original key. Keep the stock launcher available as the fallback if the custom launcher cannot open. Loss of the production key requires a new installation strategy rather than an in-place update. [Android signing key guidance](https://developer.android.com/studio/publish/app-signing#secure-key)
- Deliver the Android Studio source, workflows, release script, example manifest, development APK, local test evidence and signing and recovery runbook. Physical TV deployment and default HOME changes remain separate approval steps. The Phase 4 update flow targets only the launcher.

