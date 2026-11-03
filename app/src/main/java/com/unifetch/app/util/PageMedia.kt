package com.unifetch.app.util

/**
 * Pages with several videos (for example a video page that also plays short silent preview clips)
 * come back from yt-dlp as a list. This works out which one is the page's main video (the one the
 * link is for) and sorts the rest into short videos, other videos and images.
 *
 * Pure logic with no Android code, so it can be unit-tested.
 */
object PageMedia {

    const val FOLDER_SHORT = "UniFetcher short videos"
    const val FOLDER_OTHER = "UniFetcher other videos"
    const val FOLDER_IMAGES = "UniFetcher images"

    /** Clips this long or shorter count as short videos. */
    const val SHORT_SECONDS = 60.0

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic")

    enum class Kind {
        MAIN,
        SHORT,
        OTHER,
        IMAGE,
    }

    /** What we need to know about one item on the page. */
    data class Item(
        val ext: String?,
        val duration: Double?,
        /** False when the item has no sound (silent previews, GIF-style clips). */
        val hasAudio: Boolean,
        val height: Double?,
        val webpageUrl: String?,
        /** File size in bytes if known (exact or estimated). */
        val size: Double? = null,
    )

    /**
     * @return the kind of every item (same order, so item i is playlist item i + 1), or null if
     *   this isn't a single page with extras (for example a real playlist or channel, which is
     *   left to the normal playlist handling).
     */
    fun plan(pageUrl: String, extractorKey: String?, items: List<Item>): List<Kind>? {
        if (items.size < 2) return null
        if (!isSinglePage(pageUrl, extractorKey, items)) return null

        val candidates = items.indices.filter { !isImage(items[it]) }
        if (candidates.isEmpty()) return null
        val main =
            candidates.maxWithOrNull(
                compareBy<Int>(
                    { if (items[it].hasAudio) 1 else 0 },
                    { items[it].duration ?: -1.0 },
                    { items[it].size ?: -1.0 },
                    { items[it].height ?: -1.0 },
                    { -it }, // ties: the first one on the page
                )
            )!!

        return items.mapIndexed { i, item ->
            when {
                i == main -> Kind.MAIN
                isImage(item) -> Kind.IMAGE
                isShort(item) -> Kind.SHORT
                else -> Kind.OTHER
            }
        }
    }

    fun folderFor(kind: Kind): String? =
        when (kind) {
            Kind.MAIN -> null
            Kind.SHORT -> FOLDER_SHORT
            Kind.OTHER -> FOLDER_OTHER
            Kind.IMAGE -> FOLDER_IMAGES
        }

    fun isImage(item: Item): Boolean = item.ext?.lowercase() in imageExts

    fun isShort(item: Item): Boolean =
        !item.hasAudio || (item.duration != null && item.duration <= SHORT_SECONDS)

    /**
     * yt-dlp's generic extractor lists every video it finds on one page. Other extractors that
     * return a list only count as "one page" when every item points back at that same page;
     * playlists and channels point at different pages, so they're left alone.
     */
    private fun isSinglePage(pageUrl: String, extractorKey: String?, items: List<Item>): Boolean {
        if (extractorKey.equals("Generic", ignoreCase = true)) return true
        val page = normalize(pageUrl)
        val urls = items.mapNotNull { it.webpageUrl }
        return urls.isNotEmpty() && urls.all { normalize(it) == page }
    }

    private fun normalize(url: String): String =
        url.substringBefore('#')
            .substringAfter("://")
            .removePrefix("www.")
            .removePrefix("m.")
            .trimEnd('/')
            .lowercase()
}
