# Installation and recovery boundaries

Version0.2.0 is a debug-signed development build. It was exercised only on an API29 Android TV emulator. Physical SWTV installation and default-HOME changes require a later explicit approval; no such change is part of Phase3.

## Later ordinary-app test

After approval, verify the TV IP and ADB authorization again. Use an explicitly selected serial for every operation, verify the APK checksum and signing identity, and install as an ordinary app without clearing its data. Open `app.swtv.home/.MainActivity` explicitly from the stock launcher or ADB. Installation alone does not assign default HOME. Keep `com.google.android.tvlauncher` installed and enabled.

The0.1.0→0.2.0 upgrade uses the same package and development signer. Its preference file/key/schema remain `home` / `config` /1. A guarded emulator upgrade confirmed the saved configuration survived without a reset. This is evidence for that development path, not a production rollback guarantee.

## Recovery

If the custom home fails during ordinary-app testing, press HOME to return to the unchanged stock launcher and use TV Settings. Explicit stock HOME from Phase1 was `com.google.android.tvlauncher/.MainActivity`; verify it is still available before any future default change. No system package disable, launcher replacement or preferred-HOME shell command is needed for ordinary-app testing.

A development APK with lower versionCode cannot be installed over a newer version through a normal update. Do not use a forced downgrade or uninstall as an automatic rollback: uninstalling removes app-local configuration, and schema compatibility needs separate release validation. Preserve the earlier source/APK and a reviewed state backup. Rebuild a prior known-good implementation with an appropriately higher version and the same signing identity only after the later release/recovery process is designed and approved.

GitHub release signing, secure keys, update manifests/integrity checks, migrations and production rollback remain Phase4. The current update provider is deliberately unconfigured. Once physical deployment and recovery validation are complete, turn off network debugging as requested, then verify port5555 is closed. It has not been turned off during Phase3 development.
