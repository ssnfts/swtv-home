# Current application boundaries

Phase2 uses one Android Gradle module, `app`. These package boundaries keep Android code separate from testable state rules; extracting independent Gradle modules is Phase3 work.

| Package / entry | Responsibility |
| --- | --- |
| `MainActivity` | Foreground lifecycle, section builders, status ticks, package/time observation and explicit user actions |
| `core` | Versioned local configuration, sanitization, app sorting, dock order and module models |
| `platform/Discovery` | PackageManager/TV input enumeration and safe explicit launch handoffs |
| `platform/Preferences` | App-private JSON preference storage through ConfigCodec |
| `platform/Icons`, `IconWorkPolicy` |48-entry finished cache; one running/48 queued requests; per-generation admission and weak view bindings |
| `platform/Wallpaper` | User-granted document decoding off main thread, sampled image and gradient fallback |
| `platform/Weather`, `WeatherRequests`, `WeatherCacheState` | Opt-in HTTPS, per-city data identity, request coalescing/cancellation and bounded cache/attempt policy |
| `platform/CatalogPicker` | Immutable visible candidates and stable target selection against the refreshed catalog |
| `platform/RuntimePolicy` | Effects, date-boundary, refresh and URL policies tested on the JVM |
| `ui/HomeUi`, `SettingsController` | Native surfaces, focus links, remote-friendly settings and chooser dialogs |

App and input discovery occur on foreground refresh; expensive icon, bitmap and network work is off main. Package events debounce catalog refresh and invalidate icons. Time/timezone events reschedule automatic-theme boundaries. Minute ticks update clock/weather status without rebuilding the entire home screen. Pause removes callbacks/receivers and cancels weather work; destruction closes workers.

Preferences belong to `app.swtv.home`. No TV system setting is a persistence backend. Weather defaults off; no location permission or always-running service exists. UI catalog selections retain stable package/input IDs if the live catalog changes while a dialog is open.

The launcher delegates HDMI/input display to the device's TV software through a typed passthrough channel URI. It does not bind vendor privileged APIs or render TV streams itself. Lack of a handler is an ordinary unavailable state.

OwnTV is an independent installed application. Its default target `tv.own.owntv` is configurable; a missing-app dialog offers the requested GitHub releases URL only after explicit user selection. There is no embedded player or automatic APK installer.

The TV Hub reservation is separately `app.swtv.tvhub`, opened through ordinary package launch discovery if present. This reservation is not yet a formal cross-app IPC/deep-link contract; schema/authentication/version negotiation for future TV Hub integration belongs to Phase3. M3U, XMLTV, channel matching, Media3, caching and favorites remain absent.

The About panel reports build/version and the unconfigured release feed. An authenticated/integrity-checked update/install system is not simulated or partially enabled here; it is Phase4 work.
