# SWTV website tiles and app-like web windows

Approved by the user, 12 November 2026. This extends the approved Cyberpunk redesign;
the existing UI work and validation continue. Google TV remains default HOME.

## Browser discovery and limit

The saved physical-TV report identifies QJY Internet, package `com.qjy.browser`,
version 1.107. Its recorded provider is `com.qjy.browser.fileprovider`; the report
does not expose a bookmark provider. The user can bookmark in Internet but has not
identified a Share or Export function. This is saved device evidence, not a fresh
foreground-browser inspection. Network ADB is closed.

Android removed the global browser-bookmark interface in API 23. Consequently,
automatic access to every private QJY favorite is not a verified integration.
Do not claim synchronization, scrape the browser's private files, use root or add
an accessibility service to read its screens. If a supported export/share route is
later demonstrated, it can be integrated explicitly.

## Approaches

| Approach | Behavior | Limitation |
| --- | --- | --- |
| **SWTV Web apps - recommended** | Pin a website URL to home and open it in an app-like fullscreen window; bookmark within that window to add another tile | Existing QJY favorites need adding once by URL or Share if available; browser sessions are separate |
| Existing Internet browser | Home tile hands a URL to an installed compatible browser | Keeps that browser's session, but app-like fullscreen and URL handoff depend on the browser |
| Automatic QJY favorites mirror | New QJY favorites appear automatically | Requires a supported browser integration that the current evidence does not establish |

## Approved implementation if the recommended design is selected

Add a **Web apps** row to the Cyberpunk home. It contains saved sites and one
**Add website** control. Enter a title and HTTPS address, or enter a hostname and
default to HTTPS. The representative site supplied by the user is a test target,
not a hardcoded public-release default. The user can pin it after updating.

Each tile uses a local initial/hostname treatment rather than background network
favicon fetches. Hold OK to rename, edit the address, reorder or remove a tile.
Store up to 32 sites with stable IDs, title up to 100 characters and URL up to
2048 characters. Deduplicate normalized URLs while preserving meaningful paths,
queries and fragments. Keep this collection in a separate versioned private store
so existing launcher schema-1 settings and six app-dock positions stay compatible.

Open a selected site in a dedicated, non-exported SWTV Web Activity backed by the
installed Android WebView. Use a compact site title/origin and native controls for
Back, Reload, Bookmark, Options and Close. It has no conventional browser address
bar during viewing. Back visits page history and then returns to SWTV; Close always
returns to SWTV. HOME still opens Google TV.

The **Bookmark** control saves the current HTTPS page into Web apps. Native D-pad
navigation is primary. Include an optional remote pointer mode for pages whose
links do not support directional focus: arrows move an on-screen cursor and OK
activates it within SWTV's own WebView. It uses no system input/accessibility
permission. Keep the native toolbar reachable in either mode.

Provide **Share to SWTV** for text URLs if another app offers Android sharing.
Always show a title/address confirmation before saving; receiving a shared URL
must not automatically launch a page or alter favorites. Share availability in QJY
is not assumed. Manual Add website remains available for any HTTPS favorite.

## Compatibility and boundaries

The embedded viewer uses SWTV's own site sessions. It cannot reuse QJY cookies, so
a site may require signing in again. JavaScript and ordinary site storage support
modern pages, but no native JavaScript bridge is exposed. Keep local file/content
access off, mixed content blocked, normal certificate checks and explicit user
activation for media. No extraction of media URLs, downloader or TV Hub playback
engine is included. Native HTML fullscreen can be supported through WebView's
normal callback, without custom streaming code.

Only HTTPS is supported in the first embedded-viewer version, preserving the
launcher's existing cleartext policy. Invalid schemes and embedded credentials
are rejected. An explicit **Open in Internet** action can use a compatible external
handler; if none exists, explain that clearly. Do not change the default browser.

Keep one WebView at a time, create it only when a web tile opens, stop/destroy it on
close, and handle renderer failure without taking down the home UI. Third-party
page behavior, sign-in, DRM and media playback are compatibility tests, not a
promise that every site behaves identically to the existing Internet app.

## Delivery and validation

Keep the existing six-library graph and production identity. The app owns URL
validation, private storage and Web Activity; launcher-ui receives a small site
tile presentation contract and callbacks. No new library dependency is needed.
Combine this addition with the unpublished 0.4.0/code 4 redesign after review.

Test URL rejection/normalization, limits, duplicates, order, malformed storage,
share confirmation, saved-page persistence, tile focus, remote pointer bounds,
Back/Close, renderer lifecycle and version-3 settings retention. Use controlled
test pages for deterministic navigation; record representative-site homepage
behavior separately. Do not infer playback from a page rendering successfully.

Deliver through the configured HTTPS updater and Android confirmation, leaving
network ADB closed. Record user-reported physical results separately from emulator
tests. Stock HOME, OwnTV and HDMI behavior remain unchanged.

## Review status

The user approved fullscreen web tiles and URL/Share import on 12 November 2026,
including the absence of automatic QJY bookmark mirroring and the one-time route
for existing browser favorites. Implementation is authorized.

Sources: saved Phase 1 package/provider report; [Android bookmark API removal](https://developer.android.com/about/versions/marshmallow/android-6.0-changes#browser-bookmark-changes);
[Android WebView file-access guidance](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion).
