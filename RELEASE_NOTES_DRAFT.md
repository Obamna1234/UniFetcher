# UniFetcher for Android — release notes draft (security hardening)

> Draft for the next release. Hardening only. Review before publishing.

## Security hardening

Fixes from the October 2026 security review. **Nothing about how the app works changes**, except one new
warning when importing a template.

**What changed**
- App updates: the downloaded update is checked to make sure it's signed by the same key as your installed
  app before it's handed to the installer.
- A notification helper component is explicitly marked private.
- The network library used for updates moved from a test (alpha) version to a stable one.
- UniFetcher and its built-in browser only use secure (https) connections. http:// links opened in the
  built-in browser are switched to https:// automatically. Downloads from http:// sites still work.
- The app's private cache (where cookies are kept) can no longer be shared with other apps.
- aria2c is paused until yt-dlp has updated to 2026.06.09 or newer (a security fix in yt-dlp). Downloads
  keep working without it, and it turns back on by itself.
- Importing a command template that can run programs now shows a warning first.
- GitHub now checks the app's libraries for known security problems on every change.

**Severity:** low/medium hardening. No critical or high-severity issues were found.

## Keeping a stable fallback

The previous release remains available as a known-good fallback. Android won't install an older version over
a newer one: to go back, uninstall UniFetcher first (this clears its settings and history), then install the
older APK.
