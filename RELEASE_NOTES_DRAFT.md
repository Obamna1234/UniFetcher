# UniFetcher for Android — release notes draft (security hardening)

> Draft for the next release. No functional changes; hardening only. Review before publishing.

## Recommended version — security hardening

Small hardening fixes from the October 2026 security review. **Nothing about how the app works changes.**

**What changed**
- App updates: the downloaded update is now checked to make sure it's signed by the same key as your
  installed app before it's handed to the installer.
- A notification helper component is now explicitly marked private.

**Severity:** low/medium hardening. No critical or high-severity issues were found.

## A few recommended follow-ups (see SECURITY.md)
- Treat imported download-templates as untrusted (they can contain command directives).
- Confirm the bundled/updated yt-dlp is recent (>= 2026.6.9) given aria2c is offered.
- Narrow the file-sharing paths and disable cleartext in a network-security config.

## Keeping a stable fallback

The previous release remains available as a known-good fallback.
