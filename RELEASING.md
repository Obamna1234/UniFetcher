# How UniFetcher is released (Android and Windows)

No version ships until every step below has passed. The owner says "release it"; nothing is
published without that.

## The release gate

1. **Every push** builds Android and Windows (x64 and ARM64) and runs the unit tests and the
   dependency scan. Every fix gets a test that would have caught it; every new feature gets tests
   that it does what was asked.
2. **Smoke tests on the built apps** (also every day, so a yt-dlp or site change is caught without a push):
   - Android 7.0 (oldest supported) and Android 15 emulators: update over the last release (data
     kept), open without crashing, share a test clip and a freely licensed Wikimedia Commons file,
     and check they download, play and weren't re-encoded.
   - Windows Server 2022 (Windows 10 based), Server 2025 (Windows 11 based) and Windows 11 ARM:
     update over the last release (settings kept), open/close three times, download with the
     bundled yt-dlp and ffmpeg, uninstall (the Downloads folder untouched).
3. **yt-dlp updates**: the apps check nightly builds, retry after an update when a site breaks, and
   can roll back a broken update.
4. **Security and privacy review** of everything changed since the last release, including anything
   merged from Seal or Parabolic: only the documented connections, nothing written outside the
   chosen folder, no web text reaching a command. Anything medium or worse is fixed first. The
   review is noted in the release's description.
5. **Beta first**: release `vX.Y.Z-beta.1`, the owner installs it on the phone and PC and checks the
   list below, then `vX.Y.Z` (the release request refuses a stable version without a beta).
6. **Publishing** only from the pipeline (the Release request workflow or a version tag), after
   steps 1–4 are green on that exact commit. Signing keys live only in GitHub Secrets.

## Owner's check on the beta

- [ ] Install the beta over your current version (phone and PC): settings and history still there?
- [ ] Download one video, one audio file and a short playlist
- [ ] Share a link from another app (phone) and use the browser extension (PC)
- [ ] Turn Wi-Fi off mid-download, then back on
- [ ] Then say "release it"

## Not tested automatically

- Real Windows 10 and 11 desktops (the CI machines are Windows Server editions built on them).
- Sites that need a login, and the browser extension in a real browser.
- Physical phones (the emulators run Android 7.0 and 15).
