package com.unifetch.app

import com.unifetch.app.util.TemplateSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateSafetyTest {
    @Test
    fun normalTemplatesAreFine() {
        assertTrue(TemplateSafety.riskyOptions("-f bv*+ba/b --embed-thumbnail -o \"%(title)s.%(ext)s\"").isEmpty())
        assertTrue(TemplateSafety.riskyOptions("--no-exec --extract-audio --audio-format mp3").isEmpty())
    }

    @Test
    fun findsOptionsThatRunPrograms() {
        assertEquals(listOf("--exec"), TemplateSafety.riskyOptions("-f best --exec \"rm -rf {}\""))
        assertEquals(
            listOf("--exec-before-download"),
            TemplateSafety.riskyOptions("--exec-before-download=sh"),
        )
        assertEquals(listOf("--downloader"), TemplateSafety.riskyOptions("--downloader aria2c"))
        assertEquals(
            listOf("--external-downloader-args"),
            TemplateSafety.riskyOptions("--External-Downloader-Args 'x'"),
        )
    }

    @Test
    fun findsShortenedOptionNames() {
        assertEquals(listOf("--exec-before-download"), TemplateSafety.riskyOptions("--exec-bef x"))
    }
}
