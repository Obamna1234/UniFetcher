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

### Deferred / recommended (need your input or a decision)

1. **Imported command-templates (Medium, CWE-88).** A shared/imported yt-dlp template can contain directives
   like `--exec`. Recommended: warn on import and/or strip `--exec`/`--external-downloader`/`--downloader`.
   Deferred because it is a UX change worth designing.
2. **aria2c + yt-dlp CVE-2026-50574.** aria2c is offered as an external downloader; that CVE (fixed in
   yt-dlp 2026.6.9) is only live if the runtime yt-dlp is older. Confirm the runtime yt-dlp baseline is
   >= 2026.6.9, or gate aria2c until confirmed.
3. **FileProvider paths / cookie file (Low).** `provider_paths.xml` exposes broad roots including the cache
   (where `cookies.txt` lives). Narrow to only the shared download/APK subfolders.
4. **WebView cleartext (Low/Medium).** Add a `network_security_config.xml` disabling cleartext.
5. **`okhttp 5.0.0-alpha.10` (Low).** Move off the alpha before shipping; add CI dependency scanning.

## Reporting a problem

Open an issue on GitHub (without personal data), or contact the repository owner.
