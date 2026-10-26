package com.unifetch.app

import com.unifetch.app.util.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureReasonTest {

    @Test
    fun sessionErrorsTriggerAnAutomaticSession() {
        assertTrue(FailureReason.isSessionError("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(FailureReason.isSessionError("WARNING: [site] abc: failed to download m3u8 information: HTTP Error 410: Gone"))
        assertTrue(FailureReason.isSessionError("ERROR: This video requires cookies. Use --cookies-from-browser or --cookies"))
    }

    @Test
    fun botChecksLoginsAndRemovedVideosDoNotTriggerASession() {
        assertFalse(FailureReason.isSessionError("ERROR: Sign in to confirm you're not a bot. HTTP Error 403"))
        assertFalse(FailureReason.isSessionError("ERROR: Login required to view this video. HTTP Error 403"))
        assertFalse(FailureReason.isSessionError("ERROR: Video unavailable. This video has been removed"))
        assertFalse(FailureReason.isSessionError("ERROR: Unable to download webpage: timed out"))
        assertFalse(FailureReason.isSessionError(null))
    }

    @Test
    fun classifiesCommonErrors() {
        assertEquals(FailureReason.SITE_NOT_SUPPORTED, FailureReason.classify("ERROR: Unsupported URL: https://example.com/x"))
        assertEquals(FailureReason.BLOCKED_BY_SECURITY, FailureReason.classify("ERROR: Got a CAPTCHA page"))
        assertEquals(FailureReason.NEEDS_LOGIN, FailureReason.classify("ERROR: This video is only available for registered users"))
        assertEquals(FailureReason.VIDEO_UNAVAILABLE, FailureReason.classify("ERROR: HTTP Error 404: Not Found"))
        assertEquals(FailureReason.VIDEO_UNAVAILABLE, FailureReason.classify("ERROR: Private video. Sign in if you've been granted access"))
        assertEquals(FailureReason.DRM, FailureReason.classify("ERROR: This video is DRM protected"))
        assertEquals(FailureReason.NETWORK, FailureReason.classify("ERROR: Unable to download webpage: <urlopen error timed out>"))
        assertEquals(FailureReason.STORAGE, FailureReason.classify("OSError: [Errno 28] No space left on device"))
        assertEquals(FailureReason.OTHER, FailureReason.classify("something unexpected"))
    }

    @Test
    fun forbiddenAfterTryingASessionMeansSessionRejected() {
        val error = "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        assertEquals(FailureReason.SESSION_REJECTED, FailureReason.classify(error, sessionTried = true))
        assertEquals(FailureReason.BLOCKED_BY_SECURITY, FailureReason.classify(error, sessionTried = false))
    }
}
