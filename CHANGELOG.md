# Changelog

All notable changes to UniFetcher (called UniFetch before v0.1.2) are listed here, newest first. Version numbers follow [Semantic Versioning](https://semver.org/).

## v0.1.9 - 2026-10-26

- **New "Action needed" tab** on the download screen for sites that want a "verify you are human" check or a login. Tap **Open**, do the check yourself, tap **Done**, and that site's videos retry automatically. Everything else keeps downloading, one notification tells you, and the videos wait as long as needed (even if the app is closed)
- Failure reasons are remembered after the app restarts
- The **Action needed** tab turns orange while anything is waiting, and an orange banner on the All tab points to it
- Download cards have a quick colour code: **orange border** = needs your action, **red border** = failed, **grey border** = cancelled (key under **⋮ → What the colours mean**)
- "Retry failed" leaves "Action needed" and "Already downloaded" items alone

## v0.1.8 - 2026-10-26

- **Skip videos already downloaded** (on by default, Settings → General): uses your download history, so it recognises earlier downloads too, even from a different link to the same video. Skipped videos show "Already downloaded"; tap Retry to download one again anyway
- Fixed: **Disable preview** didn't hide thumbnails on the download screen
- The list view shows failure reasons too

## v0.1.7 - 2026-10-26

### Automatic cookies and clearer failures (spec 4a, 4e, 4i)

- **Automatic site sessions** (on by default): when a site refuses a download (e.g. HTTP 403 or 410), UniFetcher opens it in a hidden built-in browser to get its cookies, then retries once with the same browser identity. Sites are remembered; a failing session is refreshed once, never looped. Logins and "verify you are human" checks are never automated
- Pop-ups, new windows and page-started downloads are blocked in those background visits
- Failed downloads show one plain reason (site not supported, blocked by site security, session rejected, needs login, video unavailable, DRM, network problem, not enough space)
- **Copy diagnostic** (was "Copy report"): reason, cookie source, steps tried and the error, with cookie values and link tokens removed
- Downloads using cookies show a 🍪 in their progress
- **Settings → Network → Sites with saved sessions**, with Forget and Forget all. Quick wipe forgets them too
- New HOW_TO section 11

## v0.1.6 - 2026-10-26

### Temporary files and storage space (spec 4c)

- New setting **Temporary files location**: Same as download folder (default), Internal storage, or SD card / USB drive, each showing its free space
- No more silent fallback to the phone: if temporary files can't go where they should, the queue pauses and explains what to do
- Downloads pause (and continue by themselves) with a clear reason: storage almost full, SD card or USB drive removed, or the phone doesn't allow temporary files on the drive
- Before each download, UniFetcher checks there's room for its temporary files when the size is known
- If the drive is unplugged as a video finishes, the video is kept safely in the temporary folder; tap Retry once the drive is back
- Leftover temporary files are deleted when a download finishes or is removed from the list; stopped downloads keep theirs so they can continue
- **Clear temporary files** covers every location and only runs when nothing is downloading; quick wipe clears them too
- New HOW_TO section: saving to an SD card or USB drive, and temporary files

## v0.1.5 - 2026-10-23

### Storage

- **Settings → Download directory** now shows where downloads are saved while downloading: straight onto your SD card or USB drive, or on the phone first because the phone doesn't allow writing straight to that drive, with the free space
- New setting **Pause downloads when free space is below**: 2 GB (default), 1 GB, 500 MB or Off. Lowering it takes effect within 30 seconds
- The "storage almost full" warning now says which storage is low, how much is left, and where to change the limit

## v0.1.4 - 2026-10-23

### Downloads go straight to your SD card or USB drive

- With **SD card or USB drive folder** turned on and the drive plugged in, videos now download directly onto the drive. Before, each video went to the phone first, so a nearly full phone blocked downloads even with a big drive attached
- The storage guard now checks free space on the drive in that case, not on the phone
- File names that SD cards and USB drives can't store (with characters like `:` `?` `|`) are adjusted automatically
- If your phone doesn't let apps write straight to the drive, UniFetcher falls back to downloading via the phone as before

## v0.1.3 - 2026-10-23

### Saving to an SD card or USB drive

- The option is now called **SD card or USB drive folder**, and works with drives plugged in through a USB-C adapter
- The SD card folder switch wouldn't turn on after choosing a folder (it kept reopening the folder picker)
- If the SD card or USB drive was unavailable when a download finished, the downloaded file was deleted. Now it's saved to the phone instead and you're told
- Choosing an SD card or USB drive as the video folder now explains to use the "SD card or USB drive folder" option instead

## v0.1.2 - 2026-10-23

### Renamed

- **UniFetch is now UniFetcher**, because the name UniFetch is already used by other projects. It installs as an update over UniFetch and keeps all your settings and history
- New downloads go to `Download/UniFetcher`. Files already in `Download/UniFetch` stay where they are

### Fixed

- After starting downloads, tapping Settings, Commands and other screens did nothing until the app was restarted

### New

- **Share all tabs → UniFetch** now takes every link, not just the first (repeats are removed)
- The ⋮ menu above the download list works: stop all, resume stopped, retry failed, clear finished, clear failed/stopped
- **Storage guard:** with less than 2 GB free, new downloads wait instead of failing. A banner and notification explain why, and the queue carries on by itself once space is freed

## v0.1.1 - 2026-10-23

### Fixed

- UniFetch closed instantly when opened (v0.1.0). The app lock was reading settings before the app had finished starting up
- Added an automatic emulator test that opens every build, so this can't slip through again

## v0.1.0 - 2026-10-23

The first UniFetch release.

### Privacy basics (stage 3)

- App lock with a PIN or pattern, plus optional fingerprint. Choose how long UniFetch can be in the background before it locks again (immediately up to 15 minutes)
- Hide from recent apps and screenshots, on by default
- Discreet notifications: only "UniFetch" is shown, with no video titles or links, and nothing on the lock screen
- Quick wipe clears download history, cookies, browser data, cached thumbnails and cache. Downloaded files are kept
- UniFetch's data is never included in Google backups or phone-to-phone transfers
- All of these are under Settings → Privacy & security

### Automatic yt-dlp updates (stage 2)

- yt-dlp is checked for updates on every app launch, before fetching a video if the last check was over 6 hours ago, and in the background every 12 hours (on a network connection, battery not low)
- New settings: update automatically (default), ask me first, or off; channel Nightly (default), Stable or Master
- If a download fails because a site changed, UniFetch updates yt-dlp and retries once
- Settings show the yt-dlp version and when it was last checked, with a "Check now" button
- Roll back to the previous yt-dlp version if an update breaks things
- yt-dlp is never replaced while a download is running, and updates only come from the official yt-dlp GitHub releases

### Fork setup (stage 1)

- UniFetch is now its own app: new name, new app ID (`com.unifetch.app`, so it installs alongside Seal) and a new icon
- Credits Seal by JunkFood02 on the About and Credits screens and in the README
- Removed Seal's sponsor, donation, Telegram, Matrix and translation-site links
- App updates are now checked against UniFetch's own GitHub Releases
- New GitHub builds: a test APK on every push, and signed release APKs when a version tag is pushed
- Added the beginner guide HOW_TO.md

---

# Seal changelog (before the fork)

UniFetch is based on Seal. Seal's own history is kept below for reference.

All notable changes (starting from v1.7.3) to stable releases will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v2.0.0][2.0.0] - unreleased

### Notable changes from v1.13

- Concurrent downloading
- Download queue
- User interface overhaul
- Large screen support
- Resume failed/canceled download
- Backup & restore unfinished tasks in the download queue
- Select from formats/playlists in Quick Download
- Predictive back animation support for Android 14+
- Bump up minimum API level to 24 (Android 7.0)

## [v1.13.0][1.13.0] - 2024-08-18

### Fixed

- Fix the issue where exported command templates could not be imported in v1.12.x
- Fix an unexpected behavior where multiple formats would be selected

### Change

- Update `youtubedl-android` to v0.16.1
- Update translations

## [v1.12.1][1.12.1] - 2024-04-17

### Added

* Add auto update interval for yt-dlp
* Cookies page now shows the current count of cookies stored in the database

### Fixed

* Intercept non-HTTP(s) URLs opened in WebView
* Videos are remuxed to mkv even when download subtitle is disabled
* Use MD2 ModalBottomSheetLayout in devices on API < 30
* Block downloads when updating yt-dlp

### Known issues

* TextFields(IME) fallback to plain character mode when showing a ModalBottomSheet
* yt-dlp might be broken if you tried to download something while it was
  updating (`bad local file header`). To fix it, you just need to update yt-dlp again

## [v1.12.0][1.12.0] - 2024-04-05

### Added

* Search from download history
* Search from subtitles in format selection page
* Export download history to file/clipboard
* Import download history from file/clipboard
* Re-download unavailable videos
* Download auto-translated subtitles
* Remember subtitle selection for next downloads
* Remux videos into mkv container for better compatibility
* Configuration for not using the download type in the last download
* Improve UI/UX for download error handling
* Add splash screen
* Haptic feedback BZZZTT!!1!

### Changed

* Long pressing on an item in download history now selects it
* Use nightly builds for yt-dlp by default
* Migrate `Slider` & `ProgressIndicator` to the new visual styles in MD3
* Use default display name from system for locales
* Metadata of videos is also embedded in the files now
* A few UI changes that I forgot

### Fixed

* Fix a permission issue when using Seal in a different user profile or private space
* Fix an issue where the text cannot be copied in the menu of the download history
* Display approximate file size for formats when there's no exact value available
* Fix an issue causes app to crash when the selected template is not available
* Custom command now ignore empty URLs, which means you can insert URLs along with arguments in
  command templates
* Fix an issue where some formats may be unavailable when downloading playlists

### Known issues

* TextFields(IME) fallback to plain character mode when showing a ModalBottomSheet
* ModalBottomSheet handles insets incorrectly on devices below API 30

## [v1.11.3][1.11.3] - 2024-01-22

### Added

* Merge multiple audio streams into a single file
* Allow downloading with cellular network temporarily

### Fixed

* App creates duplicated command templates on initialization
* Cannot make video clip in FormatPage

## [v1.11.2][1.11.2] - 2024-01-06

### Added

* Keep subtitles files after embedding into videos
* Force all connections via ipv4
* Prefer vp9.2 if av1 hardware decoding unavailable
* Add system locale settings for Android 13+

### Fixed

* User agent gets enabled when refreshing cookies
* Restrict filenames not working in custom commands

### Changed

* Transition animation should look more smooth now

## [v1.11.1][1.11.1] - 2023-12-16

### Added

* Add `--restrict-filenames` option in yt-dlp
* Add playlist title as an option for subdirectory
* Add more thanks to sponsors

### Fixed

* Fix some minor UI bugs
* Fix an issue causing error when parsing video info

## [v1.11.0][1.11.0] - 2023-11-18

### Added

* Custom output template (`-o` option in yt-dlp)
* Export cookies to a text file
* Make embed metadata in audio files optional
* Add the ability to record download archive, and skip duplicate downloads
* Add cancel button to the download page
* Add input chips for sponsorblock categories
* Add subtitle selection dialog in format page, make auto-translated subtitles available in subtitle
  selection
* Add more thanks to sponsors

### Changed

* Move the directory for storing temporary files to external storage (`Seal/tmp`)
* Change the default output template to `%(title)s.%(ext)s`
* Temporary directory now are enabled by default for downloads in general mode
* Move actions in format page to dropdown menu
* Download subtitles are now available when downloading audio files
* `android:enableOnBackInvokedCallback` is changed to `false` due to compatibility issues

### Fixed

* Fix an issue causes sharing videos to fail on certain devices
* Fix an issue causes uploader marked as null, make uploader_id as a fallback to uploader
* Fix an issue when a user performs multiple clicks causing duplicate navigating behaviors

### Removed

* Custom prefix for output template has been removed, please migrate to custom output template

## [v1.10.0][1.10.0] - 2023-08-30

### Added

**Subtitles**

* Convert subtitles to another format
* Select subtitle language in format selection

**Format selection**

* Display icons(video/audio) on `FormatItem`s
* Split video by chapters
* Select subtitle to download by language names/codes

**Custom commands**

* Create custom command tasks in the Running Tasks page
* Configure download directory separately for custom command tasks
* Select multiple command templates to export & remove

**Cookies**

* Add `CookiesQuickSettingsDialog` for refreshing & configuring cookies in configuration menu
* Add user agent header when downloading with cookies enabled

**Other New Features & UI Improvements**

* Show `PlainToolTip` when long-press on `PlaylistItem`
* Add monochrome theme
* Add proxy configuration for network connections
* Add translations in Swedish and Portuguese

### Fixed

* App crashes when being opened in the system share sheet
* Video not shown in YouTube playlist results
* Cookies cannot be disabled after clearing cookies
* Hide video only formats when save as audio enabled
* Parsing error with decimal value in width/height
* Audio codec preference not works as expected
* Could not fetch video info when `originalUrl` is null

### Changed

**Notable Changes**

* Upgrade target API level to 34 (Android 14)
* Preferred video format changed to two options: Legacy and Quality
* UI improvements to the configuration dialog

**Other Changes**

* Update `ColorScheme`s and components to reflect the new MD3 color roles
* Update youtubedl-android version, added pycryptodomex to the library
* Move Video formats to the bottom of the `FormatPage`
* Notifications now are enabled by default
* Minor UI improvements & changes

## [v1.9.2][1.9.2] - 2023-04-27

### Fixed

* Fix a bug causing Incognito mode not working in v1.9.1
* Fix misplaced quality tags in `AudioQuickSettingsDialog`
* Fix mismatched formats when using Save as audio & Download playlist

## [v1.9.1][1.9.1] - 2023-04-11

### Added

* Add Sponsor page: You can now support this app by sponsoring on GitHub!

### Fixed

* Fix a bug causing warnings not shown in logs of completed custom command tasks
* Fix a bug causing videos not scanned into media library when private mode is enabled

### Changed

* Move the directory for temporary files to `cacheDir`

## [v1.9.0][1.9.0] - 2023-03-12

### Added

* Add Preview channel for auto-updating
* Add an option to update to Nightly builds of yt-dlp
* Add a dialog for F-Droid builds in auto-update settings
* Add a switch for auto-updating yt-dlp
* Add the ability to share files in `VideoDetailDrawer`
* Add a badge to the icon to indicate the count of running processes
* Add a switch for disabling the temporary directory
* Add format & quality preference for audio
* Add custom format sorter
* Add the ability to clip video and audio in `FormatSelectionPage` (experimental)
* Add the ability to edit video titles in `FormatSelectionPage` before downloading
* Add the ability to share the thumbnail url in `FormatSelectionPage`
* Implement a new method to extract cookies from the `WebView` database

### Changed

- Change the operation of open link to long pressing the link button in `VideoDetailDrawer`
- Change the thread number range of multi-threaded download to 1-24
- Change the status bar icon to filled icon
- Change the quick settings for media format in the configuration dialog

### Fixed

- Fix a bug causing high-quality audio not downloaded with YT Premium cookies & YT Music URLs
- UI bug in `ShortcutChip` with long template
- Fix a bug causing empty subtitle language breaks downloads
- Fix an issue causing specific languages not visible in system settings on Android 13+
- Fix a UI bug in the format selection page
- Fix a bug causing app to crash when toasting in Android 5.0
- Fix a UI bug causing LTR texts to display incorrectly in RTL locale environment
- Add legacy app icon for API 21~25

### Known issues

- Cookies may not work as expected in some devices, please try to re-generate cookies after this
  occurs. File an issue on GitHub with your device info when experience errors.

## [v1.8.2][1.8.2] - 2023-02-10

### Fixed

- Trimmed ASCII characters filename
- Unexpected error when downloading multiple video to SD card with quick download
- Error when cropping vertical thumbnails as artwork
- ID conflicts when importing custom templates

### Changed

- Add `horizontalScroll` to `LogPage`
- Revert the URL intent filters

## [v1.8.1][1.8.1] - 2023-02-01

### Fixed

- App crashes when downloading in private mode
- Unexpected ImeActions in TextFields
- Disable SD card download when the directory is not set
- Localized strings for file size texts

## [v1.8.0][1.8.0] - 2023-01-29

### Added

- Download to SD card
- Quick download in parallel
- Task dashboard & log page for custom commands
- Custom shortcuts for command templates
- Subtitle preferences
- Apply `--embed-chapters` for video downloads by default
- New color schemes for UI theming

### Changed

- New transition animation between destinations
- Change `minSdkVersion` to 21 (Android 5.0)
- Accessibility improvements to components
- Revert playlist items limit in v1.7.3
- Scan the download directory to the system media library after running commands
- Change the LongClick operations of `FormatItem` to share the stream URLs

## [v1.7.3][1.7.3] - 2023-01-10

### Fixed

- `Webview` captures Cookies from wrong domains
- Notifications of custom commands remain unfinished status
- App crashes when fails to parse video info for format selection
- App crashes when parsing channel info for playlist download

### Added

- Tips about streams merging in `FormatSelectionPage`

### Changed

- Playlist results are limited to 200 videos

[1.7.3]: https://github.com/JunkFood02/Seal/releases/tag/v1.7.3

[1.8.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.8.0

[1.8.1]: https://github.com/JunkFood02/Seal/releases/tag/v1.8.1

[1.8.2]: https://github.com/JunkFood02/Seal/releases/tag/v1.8.2

[1.9.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.9.0

[1.9.1]: https://github.com/JunkFood02/Seal/releases/tag/v1.9.1

[1.9.2]: https://github.com/JunkFood02/Seal/releases/tag/v1.9.2

[1.10.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.10.0

[1.11.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.11.0

[1.11.1]: https://github.com/JunkFood02/Seal/releases/tag/v1.11.1

[1.11.2]: https://github.com/JunkFood02/Seal/releases/tag/v1.11.2

[1.11.3]: https://github.com/JunkFood02/Seal/releases/tag/v1.11.3

[1.12.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.12.0

[1.12.1]: https://github.com/JunkFood02/Seal/releases/tag/v1.12.1

[1.13.0]: https://github.com/JunkFood02/Seal/releases/tag/v1.13.0