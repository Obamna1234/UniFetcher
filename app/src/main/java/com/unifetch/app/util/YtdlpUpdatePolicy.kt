package com.unifetch.app.util

import java.util.concurrent.TimeUnit

/**
 * Plain rules for when to check for yt-dlp updates and which download errors an update can fix.
 * Kept free of Android code so they can be unit-tested.
 */
object YtdlpUpdatePolicy {
    /** Before fetching video info, check again if the last check is older than this. */
    val FETCH_CHECK_INTERVAL: Long = TimeUnit.HOURS.toMillis(6)

    /** How often the background check runs. */
    const val BACKGROUND_INTERVAL_HOURS: Long = 12

    /**
     * After a failed download, don't ask GitHub again if we checked this recently. Stops a batch of
     * failing links from hitting GitHub's rate limit.
     */
    val RETRY_CHECK_COOLDOWN: Long = TimeUnit.MINUTES.toMillis(10)

    /** True when a check is due. Also true if the clock moved backwards since the last check. */
    fun isCheckDue(lastCheck: Long, now: Long, interval: Long): Boolean =
        lastCheck <= 0L || now < lastCheck || now - lastCheck >= interval

    // Errors that a newer yt-dlp cannot fix: the video itself is gone, private, locked or DRM.
    private val permanentErrors =
        listOf(
            "private video",
            "video unavailable",
            "has been removed",
            "been terminated",
            "http error 404",
            "http error 410",
            "drm",
            "sign in to confirm",
            "members-only",
            "members only",
            "not available in your country",
            "geo restrict",
            "geo-restrict",
            "this live event will begin",
            "premieres in",
            "copyright claim",
        )

    // Errors that usually mean the site changed and yt-dlp needs an update.
    private val updateFixableErrors =
        listOf(
            "unsupported url",
            "confirm you are on the latest version",
            "please report this issue",
            "unable to extract",
            "nsig extraction failed",
            "signature extraction failed",
            "extractorerror",
        )

    /** True if updating yt-dlp and retrying once is worth a try for this error message. */
    fun shouldUpdateAndRetry(errorMessage: String?): Boolean {
        val message = errorMessage?.lowercase() ?: return false
        if (permanentErrors.any { it in message }) return false
        return updateFixableErrors.any { it in message }
    }

    /**
     * Security (F-A, CVE-2026-50574): yt-dlp older than 2026.06.09 can run code through aria2c on
     * some HLS/DASH downloads. aria2c is only used with this version or newer.
     */
    val ARIA2C_SAFE_FROM = listOf(2026, 6, 9)

    /**
     * True if [version] (e.g. "2026.06.09" or a nightly "2026.06.10.123456") is at least
     * [minimum]. Unknown or unreadable versions count as too old.
     */
    fun isAtLeast(version: String?, minimum: List<Int>): Boolean {
        val parts =
            version?.trim()?.split('.')?.take(3)?.map { it.toIntOrNull() ?: return false }
                ?: return false
        if (parts.size < 3) return false
        for (i in 0 until 3) {
            if (parts[i] != minimum[i]) return parts[i] > minimum[i]
        }
        return true
    }
}
