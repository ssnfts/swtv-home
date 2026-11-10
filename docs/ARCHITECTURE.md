# Current application boundaries

Phase2 uses one Android Gradle module, `app`. These package boundaries keep Android code separate from testable state rules; extracting independent Gradle modules is Phase3 work.

| Package / entry | Responsibility |
| --- | --- |
| `MainActivity` | Foreground lifecycle, section builders, status ticks, package/time observation and explicit user actions |
| `core` | Versioned local configuration, sanitization, app sorting, dock order and module models |
| `platform/Discovery` | PackageManager/TV input enumeration and safe explicit launch handoffs |
| `platform/Preferences` | App-private JSON preference storage through ConfigCodec |
| `platform/Icons`, `IconWorkPolicy` |48-entry finished cache; one running/48 queued requests; per-generation admission and weak view bindings |
| `platform/Wallpaper`, `LatestResource` | Sampled document decoding off main, same-URI cache/coalescing, one running and one latest pending request, pause cancellation |
| `platform/Weather`, `WeatherRequests`, `WeatherCacheState` | Opt-in HTTPS, per-city data identity, request coalescing/cancellation and bounded cache/attempt policy |
| `platform/CatalogPicker` | Immutable visible candidates and stable target selection against the refreshed catalog |
| `platform/DockPolicy` | One effective dock sequence for display and editing; replace/swap/append and stale-pin handling |
| `platform/InputIntents`, `InputPolicy` | Explicit ACTION_VIEW/data/channel-MIME construction and current passthrough/connected-or-standby eligibility |
| `platform/RuntimePolicy` | Effects, date-boundary, refresh and URL policies tested on the JVM |
| `ui/HomeUi`, `SettingsController` | Native surfaces, focus links, remote-friendly settings and chooser dialogs |

App and input discovery occur on foreground refresh; expensive icon, bitmap and network work is off main. Package events debounce catalog refresh and invalidate icons. Time/timezone events reschedule automatic-theme boundaries. Minute ticks update clock/weather status without rebuilding the entire home screen. Pause removes callbacks/receivers and cancels weather work; destruction closes workers.

Preferences belong to `app.swtv.home`. No TV system setting is a persistence backend. Weather defaults off; no location permission or always-running service exists. UI catalog selections retain stable package/input IDs if the live catalog changes while a dialog is open.

The launcher delegates supported passthrough inputs to the device's TV software through an explicit channel MIME and passthrough URI. It revalidates membership and accepts connected/standby inputs, rejecting missing/disconnected/unsupported kinds. Tuner/software sources remain with the stock TV application; no tuner playback or privileged switching is implemented. A started Activity is not proof that actual hardware switching worked.

Stored dock pins survive read-only refresh and transient package-update gaps. A single effective sequence drives the rendered dock and user mutations; explicit edits write that sequence back. The phone-app toggle filters the grid, while already pinned phone apps remain available in the dock. Choosing an unpinned app replaces an occupied slot, an already pinned app swaps positions, and an empty position appends. Full-dock long-OK pinning explains how to choose a replacement slot.

OwnTV is an independent installed application. Its default target `tv.own.owntv` is configurable; a missing-app dialog offers the requested GitHub releases URL only after explicit user selection. There is no embedded player or automatic APK installer.

The TV Hub reservation is separately `app.swtv.tvhub`, opened through ordinary package launch discovery if present. This reservation is not yet a formal cross-app IPC/deep-link contract; schema/authentication/version negotiation for future TV Hub integration belongs to Phase3. M3U, XMLTV, channel matching, Media3, caching and favorites remain absent.

The About panel reports build/version and the unconfigured release feed. An authenticated/integrity-checked update/install system is not simulated or partially enabled here; it is Phase4 work.
