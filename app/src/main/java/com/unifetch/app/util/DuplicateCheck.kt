package com.unifetch.app.util

import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.PreferenceUtil.getBoolean
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Skips videos that are already downloaded (spec 4), using the download history, so it also
 * recognises videos downloaded before this was turned on. yt-dlp reports each video's main
 * page link, so different links to the same video (short links, tracking parameters) match.
 */
object DuplicateCheck {

    class AlreadyDownloadedException :
        Exception(App.context.getString(R.string.duplicate_skipped_msg))

    fun isEnabled(): Boolean = SKIP_DUPLICATES.getBoolean()

    /** True if this video is in the history and its file still exists. */
    fun isAlreadyDownloaded(info: VideoInfo): Boolean {
        val urls = listOfNotNull(info.webpageUrl, info.originalUrl).flatMap(::variants).distinct()
        if (urls.isEmpty()) return false
        val found =
            runCatching { runBlocking { DatabaseUtil.findDownloadsByUrls(urls) } }
                .getOrDefault(emptyList())
        return found.any { entry ->
            val path = entry.videoPath
            // Files on an SD card / USB drive are stored as content addresses that can't be
            // checked cheaply; count them as present.
            !path.startsWith("/") || File(path).exists()
        }
    }

    /** The link as given, plus with/without a trailing slash and "www.". */
    private fun variants(url: String): List<String> {
        val base = url.trim()
        val noSlash = base.trimEnd('/')
        val list = mutableListOf(base, noSlash, "$noSlash/")
        list += list.map { it.replaceFirst("://www.", "://") }
        list += list.filter { !it.contains("://www.") }.map { it.replaceFirst("://", "://www.") }
        return list
    }
}
