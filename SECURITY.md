# Security notes

## October 2026 security review

A read-only security review of UniFetcher for Android (OWASP MASVS/MASTG) was carried out. **No critical or
high-severity issues were found.** Notable strengths: no `addJavascriptInterface` bridge in the WebView,
no custom all-trusting `TrustManager`, immutable `PendingIntent`s, a salted PBKDF2 app-lock, and no
hardcoded secrets.

### Fixed in this pass (behaviour-preserving)

1. **Update APK signer check (CWE-494).** `UpdateUtil.installLatestApk()` now compares the downloaded update
   APK's signing certificate against the installed app's before handing it to the system installer. It
   **aborts only on a definite mismatch**; if the signatures match, or can't be read on a given device, it
   proceeds — so legitimate updates are never blocked. Android's installer still enforces same-signer as the
   backstop.
2. **Explicit `android:exported="false"`** on `NotificationActionReceiver` (CWE-926) — already the effective
   default on modern targetSdk; this removes any ambiguity across manifest merges.

### Fixed in the November 2026 pass (one commit each, behaviour-preserving)

3. **Dependencies (F6, CWE-1104).** okhttp (used for app and yt-dlp updates) moved from 5.0.0-alpha.10 to
   stable **4.12.0** (5.x needs a newer Kotlin than the app uses). New **Dependency scan** workflow: sends the Gradle dependency list to GitHub for
   Dependabot alerts, checks pull requests for newly added vulnerable libraries, and lists vulnerable NuGet
   packages for the Windows app. It runs on every push, pull request and weekly, and never blocks a release.
4. **HTTPS only (F5, CWE-319).** `res/xml/network_security_config.xml` turns off cleartext traffic for the
   app and its built-in browser. Links opened in the built-in browser (site checks, automatic sessions,
   cookie login) are upgraded from `http://` to `https://` first (`util/WebSafety.kt`). yt-dlp is a separate
   process and isn't affected, so downloads from `http://` sites still work. If a site the app needs only
   works over http, allow just that domain in a `<domain-config>`.
5. **FileProvider paths (F4, CWE-200/927).** The cache folder (which holds `cookies.txt` and yt-dlp's config)
   is no longer listed in `provider_paths.xml`, so it can never be shared. Shared storage stays listed,
   because downloads can be saved to any folder the user picks; the provider isn't exported, and other apps
   only get one-off access to a file UniFetcher explicitly opens or shares with them.
6. **aria2c and CVE-2026-50574 (F-A, CWE-74).** The built-in yt-dlp (youtubedl-android 0.17.3) is older than
   2026.06.09, and "Roll back" can also go back to an older yt-dlp. aria2c is therefore only used when the
   installed yt-dlp is **2026.06.09 or newer**; otherwise downloads quietly use yt-dlp's own downloader and
   the aria2c setting says it's paused. It turns back on by itself after a yt-dlp update.
7. **Imported command templates (F2, CWE-88).** When templates or shortcuts are imported, they're checked for
   yt-dlp options that can run programs or load code (`--exec`, `--exec-before-download`, `--downloader`,
   `--external-downloader` and their `-args`, `--use-postprocessor`, `--plugin-dirs`, `--ffmpeg-location`,
   `--config-location(s)`, including shortened option names). If any are found, a warning offers **Cancel** /
   **Import anyway**. Templates typed by the user aren't affected or changed.

### Decided not to change

- **Compose BOM (alpha).** The stable BOM of the same date would move Compose back from 1.8 to 1.7 and
  Material 3 from 1.4 to 1.3 (not a drop-in, could change screens), and a newer stable BOM needs AGP/Kotlin
  upgrades. Compose doesn't handle network or untrusted data, so there's no security gain. Revisit with the
  next toolchain upgrade.

### For the Windows app (see windows/SECURITY.md)

- Dependency scanning covers the Windows NuGet packages (above).
- aria2c isn't bundled and its setting is hidden, so CVE-2026-50574 doesn't apply. yt-dlp is fetched from
  the official release at build time and by the SHA-256-verified tool updater.
- No FileProvider, cleartext WebView or template import exists on Windows, so F4, F5 and F2 don't apply.

## Reporting a problem

Open an issue on GitHub (without personal data), or contact the repository owner.
