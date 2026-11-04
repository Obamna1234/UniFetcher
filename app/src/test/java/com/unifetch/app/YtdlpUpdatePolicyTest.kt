package com.unifetch.app

import com.unifetch.app.util.YtdlpUpdatePolicy
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtdlpUpdatePolicyTest {

    private val sixHours = YtdlpUpdatePolicy.FETCH_CHECK_INTERVAL
    private val now = 1_800_000_000_000L

    @Test
    fun checkIsDueWhenNeverChecked() {
        assertTrue(YtdlpUpdatePolicy.isCheckDue(lastCheck = 0L, now = now, interval = sixHours))
    }

    @Test
    fun checkIsNotDueWithinSixHours() {
        val last = now - TimeUnit.HOURS.toMillis(5)
        assertFalse(YtdlpUpdatePolicy.isCheckDue(lastCheck = last, now = now, interval = sixHours))
    }

    @Test
    fun checkIsDueAfterSixHours() {
        val last = now - TimeUnit.HOURS.toMillis(6)
        assertTrue(YtdlpUpdatePolicy.isCheckDue(lastCheck = last, now = now, interval = sixHours))
    }

    @Test
    fun checkIsDueWhenClockWentBackwards() {
        val last = now + TimeUnit.HOURS.toMillis(1)
        assertTrue(YtdlpUpdatePolicy.isCheckDue(lastCheck = last, now = now, interval = sixHours))
    }

    @Test
    fun extractorErrorsTriggerUpdateAndRetry() {
        listOf(
                "ERROR: Unsupported URL: https://example.com/watch/1",
                "ERROR: [youtube] abc: Unable to extract uploader id; please report this issue " +
                    "on https://github.com/yt-dlp/yt-dlp/issues?q= . Confirm you are on the " +
                    "latest version using yt-dlp -U",
                "ERROR: [youtube] abc: nsig extraction failed: Some formats may be missing",
                "yt_dlp.utils.ExtractorError: something broke",
            )
            .forEach { assertTrue(it, YtdlpUpdatePolicy.shouldUpdateAndRetry(it)) }
    }

    @Test
    fun permanentErrorsDoNotRetry() {
        listOf(
                "ERROR: [youtube] abc: Private video. Sign in if you've been granted access",
                "ERROR: [youtube] abc: Video unavailable. This video has been removed",
                "ERROR: unable to download video data: HTTP Error 404: Not Found",
                "ERROR: [site] abc: This video is DRM protected",
                // Even with the "please report" footer, a removed video is still removed.
                "ERROR: Video unavailable. Confirm you are on the latest version using yt-dlp -U",
            )
            .forEach { assertFalse(it, YtdlpUpdatePolicy.shouldUpdateAndRetry(it)) }
    }

    @Test
    fun networkErrorsAndEmptyMessagesDoNotRetry() {
        assertFalse(YtdlpUpdatePolicy.shouldUpdateAndRetry(null))
        assertFalse(YtdlpUpdatePolicy.shouldUpdateAndRetry(""))
        assertFalse(
            YtdlpUpdatePolicy.shouldUpdateAndRetry(
                "ERROR: Unable to download webpage: <urlopen error timed out>"
            )
        )
    }

    @org.junit.Test
    fun aria2cNeedsYtdlp20260609OrNewer() {
        val min = com.unifetch.app.util.YtdlpUpdatePolicy.ARIA2C_SAFE_FROM
        val f = com.unifetch.app.util.YtdlpUpdatePolicy::isAtLeast
        org.junit.Assert.assertTrue(f("2026.06.09", min))
        org.junit.Assert.assertTrue(f("2026.06.10.123456", min))
        org.junit.Assert.assertTrue(f("2027.01.01", min))
        org.junit.Assert.assertFalse(f("2026.06.08", min))
        org.junit.Assert.assertFalse(f("2025.12.31.000000", min))
        org.junit.Assert.assertFalse(f(null, min))
        org.junit.Assert.assertFalse(f("built-in", min))
    }
}
