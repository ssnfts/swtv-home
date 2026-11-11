# Offline release signing

The launcher release build is version 0.3.0, code 3. Its update feed and signing certificate are public build configuration. The signing key belongs to the release owner. The release script invokes Android SDK tools locally and lets `apksigner` prompt for the keystore password.

## Establish the release identity

Choose a keystore location on encrypted storage outside this project. Create a dedicated long-lived signing key there with JDK `keytool`, using its interactive password prompts. Keep an encrypted offline backup on a separate device and a separately secured password recovery record. Before the first production installation, export the public certificate from both copies and confirm their SHA-256 fingerprints match. Store the fingerprint in the release record. A lost private key prevents normal updates to installations signed by it.

The project ignores JKS, keystore and PKCS#12 files. Keep keys and passwords outside Git, GitHub Actions secrets, source archives and release assets. Local emulator validation uses a disposable test identity. Its fingerprint has no production authority.

The production certificate differs from the Android debug certificate. A same-package production APK requires a fresh production installation instead of replacing a debug-signed installation. The physical TV has yet to receive SWTV Home. Its first approved installation can use the production identity.

## Build unsigned release input

Use JDK 17, Python 3.11 or newer, Android SDK 35, Build Tools 34.0.0 and the pinned Gradle wrapper. Select a dedicated public launcher release repository. A public manifest avoids storing a GitHub access token on the TV. The future TV Hub has its own package, feed and version sequence.

The feed URL has this form, with your repository owner and name substituted.

```text
https://github.com/OWNER/REPOSITORY/releases/latest/download/swtv-home-update-v1.json
```

Pass that URL and the lowercase SHA-256 fingerprint of the public DER certificate to Gradle. These values are embedded in release application metadata. Debug builds show the unconfigured update state.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug :app:lintRelease :app:assembleRelease "-PswtvUpdateFeed=$releaseFeed" "-PswtvUpdateCert=$certificateSha256"
```

The default version comes from source. For the next reviewed release or recovery build, explicitly set `-PswtvVersionName=0.3.1` and `-PswtvVersionCode=4`, choosing a code higher than every published version. Keep the feed and certificate consistent with existing production installations.

The `Android checks` workflow runs on pull requests and pushes to main. The manually dispatched `Build unsigned launcher release` workflow accepts only the public feed and certificate as configuration. It records the checked-out commit and SHA-256 of the unsigned APK. Both workflows use full commit pins, a verified Gradle wrapper and read-only repository permissions. They contain no publishing or signing step.

Review the source revision and Actions run before downloading the unsigned artifact. Confirm its checksum against the recorded build output. A local build can use `Get-FileHash -Algorithm SHA256` after the same review. The workflows require a GitHub repository before they can execute remotely.

## Sign and prepare the bundle

With `JAVA_HOME` pointing to JDK 17, run the local script from the project. Supply the full reviewed source commit, the unsigned APK checksum, your repository, and the public certificate fingerprint. `$releaseOutput` must name a new directory in an existing parent.

```powershell
python scripts/prepare-release.py --unsigned-apk $unsignedApk --unsigned-sha256 $unsignedSha256 --build-tools $buildTools --keystore $keystore --alias $keyAlias --certificate-sha256 $certificateSha256 --repository $releaseRepository --source-commit $sourceCommit --previous-version-code $lastPublishedCode --out $releaseOutput
```

The script snapshots and checks the unsigned input, inspects its package, version, SDK levels, permissions and embedded trust configuration, aligns it, then prompts through `apksigner`. It verifies the signature and expected single signer and checks final alignment. The completed output contains the signed APK, `swtv-home-update-v1.json`, checksums, signature output and a receipt binding source revision and input and output hashes. A failed step leaves the selected release destination unpublished. Passwords are not script arguments or saved artifacts.

The example under `docs/examples` illustrates the schema. Generate release manifests from the signed bytes with this script. Its example hash and size are deliberately illustrative.

## Review and publish

Inspect the receipt, signature fingerprint, checksums and manifest. Confirm the APK works through the API 29 test path and the intended physical-device acceptance process. Review recovery readiness and the exact destination before publishing. Use the immutable tag form `swtv-home-v0.3.0` for version 0.3.0 and the asset name `swtv-home-0.3.0.apk`.

An owner with publishing authorization can prepare a GitHub draft release for the reviewed source tag, upload the generated bundle files, review the draft and then publish it as the latest launcher release. The stable manifest URL follows latest, while the manifest's APK URL names the exact versioned asset. Keep this feed dedicated to launcher releases so future TV Hub releases cannot displace its latest pointer. Preserve earlier release evidence and enable immutable releases when supported by the chosen repository policy.

## User update and recovery

The user opens About and chooses Check for updates, Download, then Install update. The app validates the package, newer version, supported SDK, bytes and signing metadata. Android verifies the APK signature and presents installation confirmation. Permission to install from this source is granted only by the user through Android Settings.

If a release is faulty, pause its distribution and rebuild known-good source with the original signing identity and a higher version code. Preserve the configuration schema or supply an explicitly tested migration. Test the replacement update before publishing it. A lower-code APK is rejected by the normal update path. Keep the stock launcher and Settings recovery route available throughout physical testing.

References for the release owner are [Android signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [GitHub Actions security](https://docs.github.com/en/actions/reference/security/secure-use) and [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).
