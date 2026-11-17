package com.unifetch.app.util

/**
 * Video finder: decides which of the requests a page's player made is the actual video stream.
 * Pure logic with no Android code, so it can be unit-tested (see [StreamFinder] for the browser part).
 */
object StreamPicker {

    enum class Kind {
        /** HLS playlist (.m3u8): what most players use. */
        HLS,
        /** DASH manifest (.mpd). */
        DASH,
        /** A plain video file (.mp4, .webm...). */
        FILE,
    }

    data class Candidate(val url: String, val kind: Kind)

    private val fileExts = listOf(".mp4", ".webm", ".m4v", ".mov", ".mkv")

    /** Pieces of a stream (not the stream itself) and things that aren't the video. */
    private val ignoredExts =
        listOf(".ts", ".m4s", ".aac", ".m4a", ".vtt", ".srt", ".jpg", ".jpeg", ".png", ".webp", ".gif")

    /** Hints that a video is a preview, advert or similar rather than the main video. */
    private val sideWords =
        listOf(
            "preview",
            "thumb",
            "sprite",
            "trailer",
            "teaser",
            "promo",
            "banner",
            "/ads/",
            "/ad/",
            "advert",
            "vast",
            "doubleclick",
            "googlesyndication",
            "init.mp4",
        )

    /** The kind of video request [url] is, or null if it isn't one. */
    fun classify(url: String): Kind? {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        val path = lower.substringBefore('#').substringBefore('?')
        val query = lower.substringBefore('#').substringAfter('?', "")
        return when {
            ignoredExts.any { path.endsWith(it) } -> null
            path.endsWith(".m3u8") || query.contains(".m3u8") -> Kind.HLS
            path.endsWith(".mpd") || query.contains(".mpd") -> Kind.DASH
            fileExts.any { path.endsWith(it) } -> Kind.FILE
            else -> null
        }
    }

    fun isSide(url: String): Boolean = url.lowercase().let { u -> sideWords.any { it in u } }

    /**
     * The most likely main video among [candidates], in the order the page asked for them, or
     * null. Prefers HLS, then DASH, then plain files; avoids previews and adverts; and among
     * equals takes the first one (a player loads its main playlist before the quality variants).
     */
    fun pick(candidates: List<Candidate>): Candidate? =
        candidates
            .distinctBy { it.url }
            .withIndex()
            .maxWithOrNull(
                compareBy(
                    { if (isSide(it.value.url)) 0 else 1 },
                    {
                        when (it.value.kind) {
                            Kind.HLS -> 3
                            Kind.DASH -> 2
                            Kind.FILE -> 1
                        }
                    },
                    { if ("master" in it.value.url.lowercase()) 1 else 0 },
                    { -it.index },
                )
            )
            ?.value

    /** Short, safe description for diagnostics: kind, host and file name, without the query. */
    fun describe(candidate: Candidate): String {
        val noQuery = candidate.url.substringBefore('?').substringBefore('#')
        val host = noQuery.substringAfter("://").substringBefore('/')
        val name = noQuery.substringAfterLast('/')
        return "${candidate.kind} stream ($host/…/$name)"
    }
}
