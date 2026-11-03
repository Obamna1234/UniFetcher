package com.unifetch.app

import com.unifetch.app.util.PageMedia
import com.unifetch.app.util.PageMedia.Item
import com.unifetch.app.util.PageMedia.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageMediaTest {
    private val page = "https://example.com/video/12345"

    private fun clip(duration: Double?, audio: Boolean = true, ext: String = "mp4", url: String? = page) =
        Item(ext = ext, duration = duration, hasAudio = audio, height = 720.0, webpageUrl = url)

    @Test
    fun picksTheLongestVideoWithSoundAsMain() {
        val kinds =
            PageMedia.plan(
                page,
                "Generic",
                listOf(clip(10.0, audio = false), clip(1500.0), clip(8.0, audio = false), clip(300.0)),
            )
        assertEquals(listOf(Kind.SHORT, Kind.MAIN, Kind.SHORT, Kind.OTHER), kinds)
    }

    @Test
    fun silentClipsAreShortEvenWithoutADuration() {
        val kinds = PageMedia.plan(page, "Generic", listOf(clip(null), clip(null, audio = false)))
        assertEquals(listOf(Kind.MAIN, Kind.SHORT), kinds)
    }

    @Test
    fun imagesAndGifsGoToImages() {
        val kinds =
            PageMedia.plan(
                page,
                "Generic",
                listOf(clip(null, audio = false, ext = "gif"), clip(600.0), clip(null, ext = "jpg")),
            )
        assertEquals(listOf(Kind.IMAGE, Kind.MAIN, Kind.IMAGE), kinds)
    }

    @Test
    fun realPlaylistsAreLeftAlone() {
        val items =
            listOf(
                clip(600.0, url = "https://example.com/video/1"),
                clip(700.0, url = "https://example.com/video/2"),
            )
        assertNull(PageMedia.plan("https://example.com/playlist/9", "ExampleTab", items))
    }

    @Test
    fun siteExtractorWithItemsFromTheSamePageCounts() {
        val items = listOf(clip(5.0, audio = false, url = "$page/"), clip(900.0, url = "https://www.example.com/video/12345"))
        assertEquals(listOf(Kind.SHORT, Kind.MAIN), PageMedia.plan(page, "ExampleSite", items))
    }

    @Test
    fun singleItemIsNotAPage() {
        assertNull(PageMedia.plan(page, "Generic", listOf(clip(100.0))))
    }
}
