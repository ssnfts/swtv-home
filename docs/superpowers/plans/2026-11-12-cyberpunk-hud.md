# Cyberpunk HUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement the renderer task and review it. Steps use checkbox syntax for tracking.

**Goal:** Deliver an original Cyberpunk-inspired SWTV 0.4.0 visual refresh that is
readable and responsive on the inspected Android 10 TV.

**Architecture:** Retain native Android Views and the six-library graph. Centralize
visual tokens, panel drawing and app-owned dialog styling in launcher-ui. Keep
discovery, configuration persistence, updater trust and TV Hub behavior intact.

**Tech Stack:** Kotlin, Android Views/Canvas/Drawable, Android API 29, compile/target
SDK 35, JDK 17, Gradle 8.9; existing dependencies only.

## Global Constraints

- Android 10/API 29; 32-bit ARM and approximately 1.65 GiB RAM.
- Google TV stays the default HOME. No system package changes or streaming work.
- Preserve `app.swtv.home`, the production signing identity and schema-1 preference semantics.
- Preserve Light, Dark and Automatic choices and saved values; do not silently rewrite stored appearance preferences.
- Primary dark palette: #090D12 background, #141C26 panel, #1D2935 raised panel, #FCEE09 yellow, #00E6F6 cyan, #F1F5F7 primary text, #A6B8C8 secondary text, #FF596D alert.
- Centralize tokens in MASTER.md and corresponding Kotlin types; no new dependencies, runtime blur, shaders, extracted game assets or perpetual animation.
- 100-140 ms ease-out focus/press transitions, maximum 1.02 focus scale; reduced-motion and low-RAM modes use immediate state changes.
- Ship as 0.4.0/code 4; keep published 0.3.0 available and never regenerate the permanent key.

## Task 1: Native visual system and screen integration

**Files:** create `MASTER.md` and focused files beneath
`launcher-ui/src/main/java/app/swtv/launcher/ui/` for tokens, angular panel drawing,
shared app-owned dialog treatment and compact dock/focus policy. Modify `HomeUi.kt`,
`HomeScreen.kt`, `SettingsController.kt`, `runtime/Icons.kt` as needed. Integrate the
shared style in app `MainActivity.kt` and `updates/UpdatePanel.kt`. Modify only
version defaults in `app/build.gradle.kts`. Add focused tests under launcher-ui.

**Interfaces:** Keep `LauncherHost`, `HomeScreenActions`, `HomeScreen.render`,
`focusedKey`, `updateClock`, `refreshWeather` and `detach` compatible. Retain Button
targets for the existing bounded Icons loader. New styling helper API can use
`HudDialogs.builder(activity, config): AlertDialog.Builder` plus a corresponding
post-show style hook; all callers must consistently style both lists and custom
views. Keep these Android helpers in launcher-ui, which app already depends on.

- [ ] Read the approved design, existing renderer/controllers and app-owned dialogs.
- [ ] Create canonical visual tokens and contrast-checked light/dark palettes.
- [ ] Before changing dock/focus logic, add focused policy tests for empty dock,
  six occupied slots, missing-package pins and removed saved focus. Verify a real
  failure before adding the changed policy. Example contract: empty pins render
  one add affordance and retain slot 0 as its configuration target; nonempty pins
  retain order and six-slot configuration access. Reuse existing FocusPolicy.
- [ ] Implement angular, opaque panels with visible default, focus, hover, pressed
  and disabled states using a stateful Drawable. Preserve minimum 4.5:1 text
  contrast and a non-colour focus border cue. Use system typefaces.
- [ ] Replace oversized timestamp/toolbar and empty dock with the approved compact
  header, utility row, OwnTV/Inputs/TV Hub cards, compact favorites and app grid.
  Use current clock format, weather opt-in, wallpaper reference and grid columns.
  Keep stable focus keys and deterministic row navigation; allow focus to scroll
  tiles fully into view without clipping the 1.02 scale/outline.
- [ ] Style app-owned lists, editors, notices, diagnostics and About/update controls
  consistently. Preserve list selection, editor keyboard behavior, cancellation,
  lifecycle and installer result handling. Do not style Android system screens.
- [ ] Correct only the missing-OwnTV release URL to
  `https://github.com/ahXN00/OwnTV/releases`; preserve the configured package target.
- [ ] Change default version to `0.4.0` and code `4`. No Gradle dependency changes.
- [ ] Run focused tests while iterating. Then run `build-local.ps1` once for all
  module JVM tests, assembly, lint and debug instrumentation assembly, and the
  module-boundary guard. Capture results under task work/cyberpunk. Do not use ADB.
- [ ] Commit source changes and write a report with exact test commands, red/green
  evidence for policy changes, files, concerns and public styling-helper API.
- [ ] Independent task review: spec compliance and code quality. Fix important
  findings with focused tests before advancing.

## Task 2: Emulator visual and migration acceptance

**Files:** root-owned guarded validation helpers in `work/cyberpunk`; evidence in
`outputs/SWTV-Cyberpunk`. Adapt the existing emulator helpers to the new layout
without weakening their nonphysical serial and API 29 guards.

- [ ] Start only the task-owned debug API 29 AVD on emulator-5556. Record code 3,
  app preference bytes and stock HOME before updating.
- [ ] Install the code-4 debug APK with data preserved. Compare schema-1 values and
  types before changing any appearance setting for the visual test.
- [ ] Select Dark through SWTV Settings on the emulator. Capture real home and menu
  screenshots, inspect them and correct spacing/clipping if needed.
- [ ] Exercise D-pad four/six-column traversal, empty/filled dock, pin/unpin, held
  action menu, last row, process recreation, light/dark, reduced motion, read-only
  diagnostics, reserved TV Hub, missing OwnTV and unconfigured debug updates.
- [ ] Run the existing four intent tests scoped to the emulator. Do not opt into
  release installer fixtures. Record limits for physical OwnTV and HDMI.
- [ ] Present actual screenshots for user visual acceptance before TV deployment.
  This checkpoint concerns appearance only; the implementation plan keeps running
  through independent review and packaging while feedback is pending.

## Task 3: Reviewed production build and delivery

- [ ] Whole-branch review from base 4c42881, including design audit, source changes,
  task review and emulator evidence. Resolve important findings in one fix wave.
- [ ] Produce APK/source/hash handoff and accurate validation report. Confirm prior
  release, debug certificate and permanent production identity remain intact.
- [ ] Push reviewed source and dispatch established hosted unsigned release build
  with the existing public feed and certificate. Verify the run and downloaded
  input hashes, then sign locally using the existing protected identity.
- [ ] After visual acceptance and an available authorized physical ADB connection,
  update the TV in place, select Dark through SWTV appearance, verify OwnTV/HDMI and
  stock HOME. Never change HOME. Keep emulator and physical results separate.
- [ ] Publish the reviewed version through the existing release process and verify
  manifest, asset hashes and current-app feed check. Close network ADB again after
  physical validation. If transport or user feedback is pending, report the exact
  completed deliverables and remaining step without claiming physical acceptance.
