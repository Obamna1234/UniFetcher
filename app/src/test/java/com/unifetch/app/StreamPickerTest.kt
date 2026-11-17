package com.unifetch.app

import com.unifetch.app.util.StreamPicker
import com.unifetch.app.util.StreamPicker.Candidate
import com.unifetch.app.util.StreamPicker.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamPickerTest {
    private fun c(url: String) = Candidate(url, StreamPicker.classify(url)!!)

    @Test
    fun recognisesStreamsAndIgnoresPieces() {
        assertEquals(Kind.HLS, StreamPicker.classify("https://cdn.example.com/v/123/master.m3u8?token=abc"))
        assertEquals(Kind.HLS, StreamPicker.classify("https://cdn.example.com/play?file=video.m3u8"))
        assertEquals(Kind.DASH, StreamPicker.classify("https://cdn.example.com/v/manifest.mpd"))
        assertEquals(Kind.FILE, StreamPicker.classify("https://cdn.example.com/v/1080p.mp4?e=1"))
        assertNull(StreamPicker.classify("https://cdn.example.com/v/seg-12.ts"))
        assertNull(StreamPicker.classify("https://cdn.example.com/v/chunk-3.m4s"))
        assertNull(StreamPicker.classify("https://example.com/player.js"))
        assertNull(StreamPicker.classify("blob:https://example.com/123"))
    }

    @Test
    fun ignoresLocalAndHomeNetworkAddresses() {
        assertNull(StreamPicker.classify("http://127.0.0.1:8080/video.mp4"))
        assertNull(StreamPicker.classify("http://192.168.1.1/cam/live.m3u8"))
        assertNull(StreamPicker.classify("http://10.0.0.5/x.mp4"))
        assertNull(StreamPicker.classify("http://172.20.1.1/x.mpd"))
        assertNull(StreamPicker.classify("http://localhost/x.mp4"))
        assertNull(StreamPicker.classify("http://[::1]/x.mp4"))
        assertNull(StreamPicker.classify("http://printer.local/x.mp4"))
        assertEquals(Kind.FILE, StreamPicker.classify("https://172.217.1.1/x.mp4"))
        assertEquals(Kind.HLS, StreamPicker.classify("https://fdcdn.example.com/x.m3u8"))
    }

    @Test
    fun prefersTheMainHlsPlaylistOverPreviewsAndVariants() {
        val picked =
            StreamPicker.pick(
                listOf(
                    c("https://cdn.example.com/previews/clip1.mp4"),
                    c("https://cdn.example.com/v/123/master.m3u8"),
                    c("https://cdn.example.com/v/123/720p/index.m3u8"),
                )
            )
        assertEquals("https://cdn.example.com/v/123/master.m3u8", picked?.url)
    }

    @Test
    fun takesTheFirstPlaylistWhenNoneIsCalledMaster() {
        val picked =
            StreamPicker.pick(
                listOf(c("https://cdn.example.com/a/playlist.m3u8"), c("https://cdn.example.com/a/1080/chunks.m3u8"))
            )
        assertEquals("https://cdn.example.com/a/playlist.m3u8", picked?.url)
    }

    @Test
    fun plainFileWhenThatIsAllThereIs() {
        val picked =
            StreamPicker.pick(
                listOf(c("https://cdn.example.com/thumbs/sprite.mp4"), c("https://cdn.example.com/video/full.mp4"))
            )
        assertEquals("https://cdn.example.com/video/full.mp4", picked?.url)
    }

    @Test
    fun aTrailerIsStillBetterThanNothing() {
        val picked = StreamPicker.pick(listOf(c("https://cdn.example.com/trailers/scene.mp4")))
        assertEquals("https://cdn.example.com/trailers/scene.mp4", picked?.url)
    }

    @Test
    fun describeHidesTokens() {
        assertEquals(
            "HLS stream (cdn.example.com/…/master.m3u8)",
            StreamPicker.describe(c("https://cdn.example.com/v/123/master.m3u8?token=secret")),
        )
    }
}
