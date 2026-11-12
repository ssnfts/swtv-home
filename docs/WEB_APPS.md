# Website tiles

Open **Web apps → Add website**, enter a title and HTTPS address, then select Save. A bare hostname is normalized to HTTPS. A blank title uses the hostname. The launcher supports 32 sites. Adding an existing normalized address reuses the existing entry; editing a different tile to the same address reports a conflict.

Hold the remote's OK button on a site tile to choose Edit / rename, Move earlier, Move later or Remove. These operations affect SWTV's tiles only. They do not change the Internet browser's favorites.

If another browser offers Android Share, choose **Add to SWTV**, review the proposed title/address and select Save. Cancel makes no change. QJY's Bookmark button alone does not share data with SWTV: existing QJY favorites require a one-time address entry or Share where available. Automatic QJY bookmark synchronization is not supported. SWTV does not inspect its private files.

## Viewer controls

- **Back** navigates this window's history, then returns to SWTV. The remote Back key first leaves fullscreen content when active.
- **Reload** retries a page and can recreate a stopped renderer.
- **Bookmark** proposes the current page's HTTPS address and title, with confirmation before saving a home tile.
- **Options** enables the remote pointer or explicitly opens the current HTTPS address in an installed Internet handler.
- **Close** returns directly to SWTV. The remote HOME button continues to open the system-selected launcher.

Use normal D-pad navigation for focusable website controls. In pointer mode, arrows move the cursor and OK clicks inside the page. MENU returns focus to the native toolbar, including from fullscreen; holding UP is another escape route. Site-specific remote navigation can vary.

## Compatibility and privacy

Websites run in SWTV's own WebView profile, separate from QJY. Existing browser logins do not transfer; a site may ask you to sign in again. Cookies and DOM storage stay in SWTV's app data. Removing a tile does not clear that profile. No site content or favicon is fetched by the launcher homepage itself.

The viewer accepts HTTPS pages and normal certificate validation. It does not bypass certificate errors, play extracted streams, download files, run arbitrary Android intents, grant camera/microphone/geolocation access or expose a native JavaScript bridge. JavaScript and DOM storage are enabled for ordinary website behavior. Media starts through a user gesture.

Pages that require popup windows, file access, unavailable codecs, DRM, special browser extensions or newer WebView APIs may not work. Use the explicit Open in Internet option when needed. Fullscreen display and bookmarks do not guarantee playback or remote compatibility for every site. Keep the TV's Android System WebView updated through its supported update mechanism.
