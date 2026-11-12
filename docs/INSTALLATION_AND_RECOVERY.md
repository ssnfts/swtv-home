# Installation and recovery

SWTV Home uses application ID `app.swtv.home` and Android API 29 or newer. The current build is 0.4.0/code 4. Production 0.3.0 is already installed on the target TV. Use its in-app updater and the same production signer to retain settings. The first-install instructions below describe provisioning a new device.

## Development upgrades

The local 0.2.0 and 0.3.0 debug builds share the existing development certificate and configuration schema 1. Preserve app data when testing updates. Debug build artifacts produced independently on a GitHub runner may have a different debug certificate. Check the fingerprint before assuming they can update an existing development installation.

The guarded emulator tools require a serial matching `emulator-<port>` and prove API 29 and emulator identity before installation or input. An upgrade test compares every saved configuration field before and after the update. Release tests use a separate disposable certificate and emulator installation.

## First physical installation

Physical TV installation requires the separate deployment approval. Recheck its IP and ADB authorization, verify the signed APK checksum and certificate, and install it as an ordinary app with an explicitly selected device. Open it from the stock app grid or by its explicit Activity. Test remote input, startup, application launch and HDMI before considering any default HOME change.

Use the production-signed APK for the TV's first installation. Keep its certificate consistent for future updates. A production APK cannot update an installation signed with a different debug certificate.

## On-TV updates

The configured release build presents Check for updates, Download and Install update. The user confirms permission to install from this source in Android Settings when needed and confirms installation in Android's package installer. Update failure, cancellation or refusal keeps the installed application available. Source permission is never granted by the updater itself.

## Recover the home screen

Press HOME to return to the stock launcher during ordinary-app testing and use TV Settings. The Phase 1 stock HOME was `com.google.android.tvlauncher/.MainActivity`. Verify that route and Settings are available before a later default-HOME change. Keep the stock package installed and enabled.

For a faulty custom launcher release, rebuild a known-good source revision with the same production key and a version code higher than the faulty release. Retain compatible preference handling and validate the resulting update before distribution. The standard updater rejects a lower version code. Uninstalling erases app-local settings and requires a separately reviewed recovery decision.

Retain earlier APKs, source revisions, receipts and the Phase 1 state snapshot. Protect the original production key and test its backup certificate. Loss of that key requires a fresh installation strategy and data-recovery planning.

## Finish deployment

After approved physical deployment and recovery validation, turn off network debugging and verify TCP 5555 is closed. On the target TV, network ADB was switched back to USB mode after the earlier deployment and three TCP 5555 probes failed while the TV remained reachable. Reboot persistence was not tested. The 0.4.0 OTA update does not require reopening ADB. Keep stock HOME selected, as the owner requested. Development tests use dedicated emulator profiles.
