# SWTV cyberpunk visual redesign

Approved by the user, 12 November 2026. Target: the existing native Android Views
launcher on SWTV-20AE, Android 10/API 29, 32-bit ARM and approximately 1.65 GiB RAM.
This is a visual refresh of the installed launcher. Google TV stays the default
HOME, following the user's explicit decision. OwnTV and HDMI are working targets.

## Direction and alternatives

| Approach | Result | Trade-off |
| --- | --- | --- |
| **Cyberpunk HUD - recommended** | Deep charcoal, electric yellow identity, cyan focus, clipped corners and a compact status header | Strongest change from the current large rounded cards; touches layout spacing and focus geometry |
| Restrained neon | Dark panels, mostly cyan outlines, yellow highlights on selection | Quieter at TV distance, less evocative of the requested reference |
| Colour-only refresh | Recolour existing cards and retain all current sizes | Lowest change, retains the oversized toolbar and empty dock that limit the current screen |

Use the first direction. Create original SWTV geometry and typography inspired by
the game's interface language. Keep the SWTV identity, installed app icons and
functional app names; use original decorative shapes rather than game artwork,
logos, characters or extracted interface assets.

## Visual thesis

A dark charcoal TV dashboard with electric-yellow identity, cyan selection edges,
bold condensed headings and small monospace metadata, arranged on an 8 dp spacing
rhythm with generous label room and flat, sharply clipped panels.

Canonical palette for the primary dark presentation:

| Token | Value | Use |
| --- | --- | --- |
| Background | `#090D12` | Main surface |
| Panel | `#141C26` | Unfocused cards |
| Raised panel | `#1D2935` | Menus and grouped controls |
| Yellow | `#FCEE09` | Brand strokes, active selection fill |
| Cyan | `#00E6F6` | Focus outline and secondary highlights |
| Primary text | `#F1F5F7` | Labels and app names |
| Secondary text | `#A6B8C8` | Date, descriptions, small status labels |
| Alert | `#FF596D` | Error accents, always accompanied by text |

Use system condensed sans for headings, normal sans for app names and readable
body text, and system monospace for short status labels. No font download is
required. Focused yellow fills use charcoal text. Check text contrast at 4.5:1 or
better; selected controls also have a border/shape cue, not colour alone.

## Screen composition

1. Compact status header: SWTV wordmark, a prominent time, smaller date and optional
   weather. Use the device's time format and current weather opt-in.
2. Compact utility controls for Settings, System Settings, About and Diagnostics.
3. Distinct Watch & Connect cards for OwnTV, HDMI/Inputs and the reserved TV Hub.
4. A shorter dock that prioritizes actual favorites. Replace six tall empty
   placeholders with one Add favorite affordance when empty. Retain all six dock
   positions and replacement/reordering through Settings.
5. App grid with current column preferences, larger icon treatment and legible
   two-line names. Preserve hidden apps, app discovery and long-OK actions.

Thin static line details and corner cuts provide the HUD treatment. Wallpaper
selection remains available. Ensure an opaque-enough card surface preserves label
contrast over user-selected images. Apply the palette to app-owned menus and
dialogs as well as the home surface. Android Settings, permission prompts and the
system installer keep their platform appearance.

Keep the current Light, Dark and Automatic choices and saved values. The intended
TV presentation uses Dark, selected through SWTV's own appearance setting after
preview acceptance. Define a coordinated light variant so existing Light/Automatic
choices remain functional; do not silently rewrite stored appearance preferences.

## Interaction thesis

Use 100-140 ms ease-out focus/press transitions, a clear cyan edge with a restrained
yellow active fill, stable native scrolling without parallax, and a maximum 1.02
focus scale; reduced-motion and low-RAM modes use immediate state changes without
flashes, perpetual glitch loops, bounce, or background animation.

D-pad is primary. Preserve deterministic left/right/up/down traversal, saved focus
keys, OK activation and held-OK menus. Mouse hover, when present, gets a quieter
border treatment than keyboard/remote focus. Do not animate layout dimensions.
Unavailable and disabled controls remain readable and cannot steal focus. Any
header/dock size change must leave the focus outline inside visible bounds.

## Implementation boundary

Keep the current six-library architecture and native Android Views renderer. The
TV's memory budget and working focus behavior make a toolkit migration unnecessary.
Add centralized Kotlin drawing, colour, typography and motion tokens, documented
in `MASTER.md`; renderer and app-owned dialogs consume them. Use Android 10 drawing
and animation APIs with no additional dependencies, shaders or runtime blur.

Preserve `app.swtv.home`, the production signing identity and schema-1 preference
semantics. No streaming work, HOME change or system package changes are included.
Correct the OwnTV missing-app release link to the official upstream release source
explicitly linked by the selected fork, following the verified Phase 5 finding.

Ship the accepted refresh as 0.4.0/code 4 through the established unsigned hosted
build and local production-signing path. Keep the published 0.3.0 release available.
Do not regenerate the permanent key. Its separate offline backup is still an owner
recovery task.

## Review and verification

Start with an actual Android TV emulator screenshot of the home surface and an
app-owned menu, then adjust the visual treatment before physical deployment.
Verify D-pad traversal at four and six columns, first/last tile clipping, empty and
filled dock states, OwnTV launch, input selection, long OK, dark/light appearance,
wallpaper contrast, reduced motion and settings retention across the code-3 to
code-4 update. Exercise the live feed on the accepted signed build.

Run existing module boundaries, relevant JVM tests, assembly and lint. Add focused
coverage for any changed dock/focus policy rather than snapshotting every colour.
Record emulator and physical results separately; physical held OK and HDMI require
the TV. Network ADB was closed after the previous deployment, so TV installation
will require re-establishing it physically. Close it again after validation.

## Approval

User approved the recommended visual and interaction theses and this written
scope in the approval reply. Implementation is authorized.
