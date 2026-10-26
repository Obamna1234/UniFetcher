package com.unifetch.app.util

/**
 * One plain-language reason for a failed download (spec 4e), worked out from yt-dlp's error text.
 * Pure logic with no Android code, so it can be unit-tested.
 */
enum class FailureReason {
    /** yt-dlp can't read this site; cookies won't help. */
    SITE_NOT_SUPPORTED,
    /** CAPTCHA or bot check. */
    BLOCKED_BY_SECURITY,
    /** Cookies were tried (and refreshed) and the site still refused. */
    SESSION_REJECTED,
    NEEDS_LOGIN,
    /** Removed, private or region-locked. */
    VIDEO_UNAVAILABLE,
    DRM,
    NETWORK,
    /** Not enough storage space (see [StorageGuard]). */
    STORAGE,
    OTHER;

    companion object {
        private val drm = listOf("drm protected", "drm-protected", "is drm", "this video is drm")

        private val unavailable =
            listOf(
                "private video",
                "video unavailable",
                "video is unavailable",
                "has been removed",
                "been terminated",
                "no longer available",
                "http error 404",
                "404: not found",
                "http error 451",
                "not available in your country",
                "not available in your region",
                "geo restrict",
                "geo-restrict",
                "geoblocked",
            )

        private val security =
            listOf(
                "captcha",
                "not a bot",
                "are a robot",
                "verify you are human",
                "verify that you are human",
                "cloudflare",
                "just a moment",
                "bot check",
                "challenge page",
                "rate-limit",
                "too many requests",
                "http error 429",
            )

        private val login =
            listOf(
                "login required",
                "log in to",
                "sign in to view",
                "requires authentication",
                "account authentication",
                "use --username",
                "members-only",
                "members only",
                "only available for registered users",
                "only available to subscribers",
                "premium members",
            )

        private val notSupported =
            listOf(
                "unsupported url",
                "is not a valid url",
                "no video could be found",
                "no media found",
                "no video formats found",
                "unable to extract",
                "please report this issue",
            )

        private val network =
            listOf(
                "timed out",
                "timeout",
                "unable to download webpage",
                "urlopen error",
                "connection reset",
                "connection refused",
                "connection aborted",
                "network is unreachable",
                "temporary failure in name resolution",
                "name or service not known",
                "no address associated",
                "getaddrinfo",
                "remote end closed connection",
                "ssl:",
                "http error 5",
            )

        private val storage =
            listOf("no space left", "not enough space for temporary files", "disk full")

        /** Errors that a fresh browser session (cookies) might fix: 401, 403, 410 and similar. */
        private val sessionErrors =
            listOf(
                "http error 401",
                "http error 403",
                "http error 410",
                "403: forbidden",
                "410: gone",
                "401: unauthorized",
                "use --cookies",
                "--cookies-from-browser",
            )

        /**
         * True if getting (or refreshing) the site's cookies is worth a try for this error.
         * Bot checks and logins aren't: they need the user (spec 4a, 4i).
         */
        fun isSessionError(message: String?): Boolean {
            val m = message?.lowercase() ?: return false
            if (security.any { it in m } || login.any { it in m }) return false
            if (unavailable.any { it in m } || drm.any { it in m }) return false
            return sessionErrors.any { it in m }
        }

        /**
         * @param sessionTried true if cookies were used (and refreshed) and it still failed, so a
         *   401/403/410 means the site rejected the session.
         */
        fun classify(message: String?, sessionTried: Boolean = false): FailureReason {
            val m = message?.lowercase() ?: return OTHER
            return when {
                storage.any { it in m } -> STORAGE
                drm.any { it in m } -> DRM
                unavailable.any { it in m } -> VIDEO_UNAVAILABLE
                security.any { it in m } -> BLOCKED_BY_SECURITY
                login.any { it in m } -> NEEDS_LOGIN
                sessionErrors.any { it in m } ->
                    if (sessionTried) SESSION_REJECTED else BLOCKED_BY_SECURITY
                notSupported.any { it in m } -> SITE_NOT_SUPPORTED
                network.any { it in m } -> NETWORK
                else -> OTHER
            }
        }
    }
}
