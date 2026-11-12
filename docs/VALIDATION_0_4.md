# 0.4.0 Cyberpunk and website validation

Version 0.4.0/code 4 retains application ID `app.swtv.home`, Android 10/API 29 minimum, target SDK 35, the six-library architecture and the original launcher settings schema. The owner approved both written designs and the actual emulator home layout, including Web apps, before release preparation. Stock Google TV remains the selected HOME.

## Completed integration checks

The combined Cyberpunk and website candidate passed a 0.3.0/code 3 to 0.4.0/code 4 debug upgrade on the task Android TV API 29 emulator. Every original schema-1 configuration field was retained. Subsequent candidate installs also retained the original preference bytes.

Eleven emulator UI checks passed: manual URL normalization/save, opening a saved home tile, Close returning to SWTV, Share review/Cancel without mutation, explicit Share Save, rejection of unsafe shared input, held-click rename with stable ID, reorder by ID, restart persistence and D-pad movement between website tiles, removal preserving its neighbor, and unchanged stock HOME. UI checks used targeted taps, text injection, directional keys and a stationary touch hold for long-click. They are not physical remote acceptance. Fixture website storage was restored and launcher preference bytes were unchanged.

The representative public homepage supplied by the owner rendered in the emulator. This was a homepage-only observation: no login, media selection or playback was tested, and the address is not a product default. Emulator WebView was 83.0.4103.101; the earlier physical-device report recorded 156.0.8078.47. These are separate compatibility surfaces.

## Automated checks and review

The final candidate passed all 110 JVM tests in 30 suites with zero failures, errors or skips. Debug assembly, instrumentation assembly and lint completed successfully. Exact source revisions, lint warnings and build transcripts are retained in the delivery evidence. No new production dependency or Android permission was added for websites.

All seven explicitly opted-in browser-platform tests passed on API 29. They cover component/export boundaries and missing IDs; secure WebView settings, MENU and renderer-crash recovery; Share confirmation and persistent invalid forms; repeated saved IDs/recreation with one owned WebView; native/Bookmark title retention after a same-document history callback; actual D-pad pointer touches and bounds; and controlled HTML fullscreen with Back/MENU restoring native controls. The ordinary runner passed four intent regressions and reported 11 assumption skips (four earlier fixtures plus seven browser fixtures). Both runs preserved launcher preference bytes and cleaned their synthetic favorites.

The title regression reproduced the original failure before the production fix. The controlled HTML uses `loadDataWithBaseURL` and an explicit HTTPS history URL. WebView 83 keeps that native virtual URL when JavaScript changes its document URL with pushState. The test checks DOM history change, the real history callback, native title and Bookmark's proposed title; it does not claim real-network same-document URL coverage. Pointer/fullscreen tests use Android's real key dispatcher to model a remote leaving touch mode.

The combined candidate passed 16 launcher smoke checks and six additional layout checks, including four/six columns, empty/full dock, missing-pin recovery, focus outline bounds and light appearance. Synthetic settings were restored afterward. Reduced-motion behavior and opaque text contrast have focused policy/source coverage. An actual bright wallpaper provider, physical frame pacing and real remote held-OK behavior remain device acceptance work.

Task reviews covered the renderer, website store and viewer. Numeric hostname aliases and failed-commit retry handling were corrected during the storage review. A same-document page-title issue was reproduced before its fix. Final whole-branch review and exact signed-build provenance are recorded separately in the release delivery report.

## Physical and release boundaries

The earlier production 0.3.0 installation and separate OwnTV 5.2.2 remain the physical baseline. The owner reported navigation, app launching and HDMI switching working and chose to keep the stock default launcher. Those observations do not prove the new release's behavior.

The production build uses the existing offline signing identity and dedicated HTTPS feed. Hosted build provenance, signed APK checksum/certificate, public-feed verification and physical OTA confirmation belong to the release delivery record. Development success alone is not a physical deployment claim.

Network ADB was previously changed back to USB mode and three TCP 5555 probes failed while the TV still answered ping. Reboot persistence was not tested. The new release can use the already configured in-app updater without reopening ADB. No default-HOME change, package disabling, firmware operation or browser-private-data access is part of this change.
