# SWTV native HUD visual system

The approved 0.4.0 system stays within Android Views/Canvas on API 29. It uses no
additional dependencies, downloaded fonts, shaders, runtime blur or game assets.
`launcher-ui` owns these tokens and drawing; app-owned dialogs consume the same
helpers. Android Settings, document pickers and the package installer remain stock.

## Colour and type

| Token | Dark | Light |
| --- | --- | --- |
| Background | #090D12 | #E9EFF2 |
| Panel | #141C26 | #F8FAFC |
| Raised panel | #1D2935 | #DCE6EC |
| Yellow active fill | #FCEE09 | #FCEE09 |
| Cyan focus edge | #00E6F6 | #006B78 |
| Primary text | #F1F5F7 | #101C26 |
| Secondary / disabled text | #A6B8C8 | #40576A |
| Alert, accompanied by text | #FF596D | #B52239 |
| Resting edge | #506477 | #677E8E |

Yellow fills always use #090D12 text. The light variant darkens cyan for a visible
edge. Primary text is at least 13.47:1 against its panel/raised surfaces; secondary
and disabled text is at least 5.94:1. Active text on yellow is 16.12:1. `HudTokens`
is the canonical Kotlin source. Text and panels remain fully opaque, including
header/section labels over chosen wallpapers. No control reduces text opacity.

`HudTokens.heading` is system sans-serif-condensed bold; body and app labels use
system sans-serif; date/status metadata uses system monospace. Sizes passed to
TextView are sp: wordmark 32, time 28, Watch cards 21, headings 16, app names 16,
utility actions 15, compact favorites 14, metadata/help 13. Names allow two lines.
Long picker entries allow three lines with ellipsis and retain their full text.

Light, Dark and Automatic retain their schema-1 values and the existing local-time
06:00/20:00 boundaries. Saved Accent changes the small section rail; yellow brand
and cyan focus preserve consistent meanings. No appearance preference is rewritten.

## Geometry and motion

All geometry converts dp through `HudTokens.dp`; raw px are used only for bitmap
buffers and Canvas values after conversion. Text uses sp, so the 320dpi TV gets the
same logical layout as emulator displays. An 8dp rhythm governs gaps and padding,
with 4dp half-gutters. The root has 24dp horizontal, 16dp top, 24dp bottom padding.

`HudPanel` draws an opaque 8dp cut-corner surface, 1dp resting/hover edge, and a
2.5dp cyan focus/selected edge entirely inside its bounds. Focus/selection also
adds an inset corner bar. Hover gets the quieter 1dp edge. Pressed/list-selected
states use yellow fill and charcoal text. Disabled text stays readable and the
update panel explicitly removes disabled actions from focus traversal.

`HudControls` uses 120ms ease-out focus/press motion with at most 1.02 scale. Reduced
motion, low-RAM devices and disabled system animators use immediate state changes.
Native scrolling follows the same motion policy. No layout dimensions, wallpaper
or background details animate. Focus asks the ScrollView to reveal an additional
8dp perimeter; row gutters and root edges leave room for the scale.

The header combines SWTV, a device-format clock, separate date and opt-in weather.
Utility actions are 40dp minimum, Watch & Connect cards 64dp, favorites 56dp, grid
apps 120dp. Dock rows use six fixed-width cells, showing only occupied entries and
one Add favorite cell when capacity remains. Grid rows preserve the saved column
count and leave unused final-row cells empty. Favorites omit the large grid icon
treatment to keep the dock short; grid icons display at 48dp.

## APIs and behavior

- `HudTokens`: palettes, typography, dp conversion, accent rail and motion gate.
- `HudPanel(context, palette, raised)`: stateful native cut-corner Drawable.
- `HudControls.button(button, config, scale)`: shared Button appearance and focus.
- `HudDialogs.builder(activity, config)`: AlertDialog.Builder; its create/show hook
  styles app-owned labels, editors, controls and lists. `HudDialogs.style(dialog,
  config)` can reapply the treatment after content changes. Its list adapter keeps
  original items, IDs, enabled state and click listener positions. Do not replace
  the builder's OnShowListener without explicitly calling the style hook.
- `HudHeader`: compact status view with minute-driven `updateClock()`.
- `DockPresentation.pins/entries`: canonical stored sequence and compact entries.
  Package discovery never removes a missing pin from this editable sequence.
  Missing entries keep their package focus key and open slot recovery; all six
  replacement/reorder positions remain available in Settings. No refresh saves
  or migrates preferences. The existing DockPolicy still bounds and deduplicates.

HomeScreen/LauncherHost lifecycle interfaces and focus keys remain compatible.
The existing bounded Icons loader still binds Buttons and retries on focus. It
renders a 48dp bitmap (capped at 144px), caches at most 48, retains at most 49 queued
or active work items, and keeps weak current-screen bindings. Missing icons do not
block labels or input.

## Validation boundary

JVM policy tests cover empty/partial/full dock, all six replacement positions,
missing-package recovery order, and invalid saved focus. Full local validation is
module tests, assembly, lint, debug instrumentation assembly and the module guard.
Real emulator layout/D-pad and physical OwnTV/HDMI acceptance are separate checks;
source tests alone do not establish visual or physical acceptance.
