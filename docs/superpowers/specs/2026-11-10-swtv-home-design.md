# SWTV Custom Home — Phase 2 design proposal

Date: 10 November 2026. Status: proposed implementation design; Phase 2 scope is authorized.

## Scope and deliverables

Build a native launcher for the inspected SWTV-20AE, Android 10/API 29, 32-bit ARM runtime, 1080p display, approximately 1.65 GiB visible RAM. Deliver an Android Studio source project, an initial debug-signed APK, automated test results, build instructions and a feature/validation record. Physical installation and default-HOME selection remain outside this development step.

## Approach

Recommended: Kotlin with Android Views and small focused classes. Use platform drawing and bounded icon caches; avoid a browser runtime or a streaming engine. Compose remains an alternative but adds a larger UI dependency surface. Forking a reference launcher offers a head start but also couples this project to its existing behavior, dependencies and license. The recommendation is original implementation informed by reference behavior, without copying source or running repository installation/debloat scripts.

Use application ID `app.swtv.home`, minimum SDK 29, compile/target SDK 35, and no native libraries. The proposed reproducible toolchain is JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and Kotlin 2.0.21. Expose a normal Leanback launch entry and a HOME-capable activity. Do not request or set default HOME programmatically. Do not add an accessibility remapper, root commands, privileged permissions, boot receiver or persistent service.

## Home screen and visual style

An uncluttered landscape screen has a small top bar for Inputs, Apps and Settings, a large clock/date, a six-slot app dock, a configurable app grid and a compact module row. Use dark translucent panels, a restrained cool accent, rounded cards, thin highlight borders and clear focus outlines. Built-in day/night gradient wallpapers respond to local time; the user can also select an image through Android's document picker.

API 29 uses simulated glass and cached wallpaper treatment, not RenderEffect. Focus animation is short, based on scale/alpha and disabled through an app-level reduced-motion setting. Avoid continuously moving backgrounds. Cache only bounded, appropriately sized artwork and release screen callbacks when paused.

## Remote navigation and app configuration

Every control is focusable, visibly focused and operable through D-pad/OK/Back. Navigation follows rows predictably; Back dismisses the active dialog or returns from settings to the home view. System HOME handling is not intercepted.

Discover enabled applications with Leanback launch activities, with an optional regular-launcher fallback for sideloaded apps. Deduplicate by package/component, sort labels consistently and refresh on foreground/package changes. Launch explicit discovered components and handle removal or unavailable activities with a visible message.

Long-press OK opens a menu for pinning/unpinning, hiding from the app grid and configuring a module. Dock slot selection and reorder controls are remote-operable and persist locally. Grid columns and hidden apps are configurable. Removing an app must not leave a broken dock slot or lose keyboard focus.

## Widgets, themes, inputs and module tiles

Clock/date use the TV's locale/time zone and update while the app is visible. Optional weather is off by default. The user explicitly enables it and enters a location; use an HTTPS weather provider without requiring device location permission. Failures show an unavailable/stale state, and refreshes are bounded while the launcher is foregrounded.

Themes include dark, light and automatic time-based mode, accent selection, reduced motion and custom wallpaper. Preferences are app-local, schema-versioned and retained across process recreation.

Enumerate TV inputs using TvInputManager. Passthrough shortcuts use the platform TV channel URI and channel MIME type discovered in Phase 1. Gate unavailable inputs and show a clear fallback when an activity cannot be resolved. This is implementation support only; real HDMI switching needs later TV validation.

Custom module tiles contain a user label and a selected installed app or TV input. A permanent TV Hub entry launches `app.swtv.tvhub` only when installed; otherwise it explains that TV Hub is reserved for future development. No M3U, XMLTV, playback or streaming code is included.

## Settings and updates

Settings configure dock/grid, themes, wallpaper, widgets, modules and motion. They also provide ordinary system Settings access and an About/Updates panel showing the installed version and build identity. Phase 4 supplies the hosted manifest, release signing and verified installation pipeline; the Phase 2 screen must honestly identify that release updates are not configured rather than simulate a successful update check.

## Internal boundaries

Separate UI/focus behavior, preference/theme configuration, installed-app discovery, TV-input discovery, widget data and TV Hub launching into focused packages with explicit models. Keep pure ordering/configuration rules independent of Android so they can be tested on the JVM. Independent Gradle modules and the formal TV Hub integration contract remain Phase 3 deliverables.

## Verification and handoff

Write behavior tests for dock ordering/deduplication, stale package removal, configuration round trips, module validation and effect policy before their implementations. Build and lint the application; inspect the APK's minimum SDK, package identity and manifest permissions. Use an Android TV emulator if the environment supports one, otherwise record the limitation explicitly and avoid claiming visual/remote validation. Record APK SHA-256 and source build commands.

Do not install on the physical TV, switch its input, change preferred HOME, alter system settings, disable packages or turn off debugging in this phase. A later deployment checkpoint must first verify stock-launcher recovery, and default-HOME changes always require separate explicit approval.
