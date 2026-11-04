package com.unifetch.app.util

/**
 * Security (F2, CWE-88): a command template is passed to yt-dlp as-is, and some yt-dlp options can
 * run other programs. Templates the user types are their own business; templates imported from
 * someone else are checked so the user can be warned before importing them.
 *
 * Pure logic with no Android code, so it can be unit-tested.
 */
object TemplateSafety {
    /** yt-dlp options that can run programs or load code. */
    private val risky =
        listOf(
            "--exec",
            "--exec-before-download",
            "--external-downloader",
            "--external-downloader-args",
            "--downloader",
            "--downloader-args",
            "--use-postprocessor",
            "--plugin-dirs",
            "--ffmpeg-location",
            "--config-location",
            "--config-locations",
        )

    /**
     * The risky options found in [template] (empty if none). yt-dlp accepts shortened option names
     * (like `--exec-bef`) and `--option=value`, so both are caught.
     */
    fun riskyOptions(template: String): List<String> =
        template
            .split(Regex("\\s+"))
            .map { it.trim('"', '\'').substringBefore('=').lowercase() }
            .filter { it.startsWith("--") && it.length >= 5 }
            .mapNotNull { token -> risky.firstOrNull { it == token || it.startsWith(token) } }
            .distinct()
}
