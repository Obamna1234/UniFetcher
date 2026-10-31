<!-- UNIFIED REPO NOTE (added during consolidation) -->
> **Platforms:** This repository now hosts UniFetch for **all operating systems** in one place:
> the **Android** app at the repository root, and the **Windows** app under [`windows/`](windows/).
> Each has its own build in CI. (Migration in progress — see the PR that introduced this.)

<div align="center">

<img src="docs/icon.png" width="120" alt="UniFetcher icon" />

# UniFetcher

A private, open-source video and audio downloader for Android.

<sub>Formerly called UniFetch.</sub>

[Download the latest release](https://github.com/Obamna1234/UniFetcher/releases/latest) · [How-to guide](HOW_TO.md) · [Changelog](CHANGELOG.md)

</div>

## What is UniFetcher?

UniFetcher is a personal Android app for downloading video and audio from the many sites supported by [yt-dlp](https://github.com/yt-dlp/yt-dlp). It is installed directly from this repository's Releases page (it is not on the Play Store).

**UniFetcher is a fork of [Seal](https://github.com/JunkFood02/Seal) by JunkFood02.** Almost everything that works today comes from Seal's excellent work. Many thanks to JunkFood02 and all of Seal's contributors and translators. UniFetcher adds extra privacy, reliability and browsing features on top.

## Privacy

- **No tracking of any kind.** No analytics, crash reporting, telemetry, ads, Firebase or Google Play Services.
- **Everything stays on your phone.** There are no UniFetcher accounts or servers.
- **The only internet connections** are to sites you choose to visit or download from, the official yt-dlp GitHub releases (to keep yt-dlp up to date), and this repository's Releases page (to check for UniFetcher updates).
- Crash reports are shown on your phone only. Nothing is sent unless you copy it and report it yourself.
- Optional extras that contact a third party, such as yt-dlp's SponsorBlock option, are off unless you turn them on.

## Features

Available now:

- Download video or audio from any site supported by yt-dlp, including playlists
- Choose formats and quality, embed subtitles, thumbnails and metadata
- Several downloads at once, with a download history
- Custom yt-dlp commands and templates
- Share a link to UniFetcher from any app to start a download
- Material You design with light and dark themes, in many languages
- Automatic yt-dlp updates (Nightly by default), with auto-retry when a site breaks and one-tap rollback
- App lock (PIN, pattern or fingerprint), hidden from recent apps and screenshots
- Discreet notifications, quick wipe, and no Google backups of app data

Planned (see [the build plan](#roadmap)):

- Encrypted private vault with a built-in video player
- Built-in private browser with up to 1000 tabs and video detection
- Unlimited queue, duplicate detection and batch downloads
- Disguised icon, folders and tags, and incognito mode

UniFetcher does not support DRM-protected content and never will.

## Install

1. On your phone, open the [latest release](https://github.com/Obamna1234/UniFetcher/releases/latest).
2. Download `UniFetcher-<version>-arm64-v8a.apk`. That works on almost every modern phone. If you're not sure, use the `universal` APK instead.
3. Open the downloaded file and allow "Install unknown apps" for your browser or file manager when asked.

Step-by-step help, including updating and fixing failed installs, is in **[HOW_TO.md](HOW_TO.md)**.

UniFetcher uses its own app ID (`com.unifetch.app`), so it installs alongside Seal instead of replacing it. It needs Android 7.0 or newer.

## Roadmap

1. ✅ Fork setup: new name, app ID, icon, credits, GitHub builds
2. ✅ Automatic yt-dlp updates and auto-retry
3. ✅ Privacy basics: app lock, screenshot blocking, discreet notifications, backup exclusion, quick wipe
4. Encrypted vault and built-in player
5. Built-in browser with tab suspension and the 1000-tab limit
6. Video detection in the browser
7. Queue improvements, duplicate detection, batch downloads
8. Disguised icon, organising downloads, incognito mode

## Building

You don't need a computer. GitHub Actions builds every change automatically:

- Every push builds a **test APK**. Find it under the [Actions](https://github.com/Obamna1234/UniFetcher/actions) tab → the latest "Build" run → **Artifacts**.
- Pushing a version tag such as `v0.1.0` builds **signed release APKs** and publishes them on the [Releases](https://github.com/Obamna1234/UniFetcher/releases) page.

See [HOW_TO.md](HOW_TO.md) for details.

## Credits

- [Seal](https://github.com/JunkFood02/Seal) by JunkFood02 and contributors, which UniFetcher is based on
- [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android), [FFmpeg](https://ffmpeg.org/) and [aria2](https://github.com/aria2/aria2)
- The full list of libraries and their licences is in the app under Settings → About → Credits

## Licence

UniFetcher is free software under the [GNU General Public License v3.0](LICENSE), the same licence as Seal. Seal's original copyright notices are kept.
