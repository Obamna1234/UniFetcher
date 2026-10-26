package com.unifetch.app.util

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.unifetch.app.util.FileUtil.getCookiesFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * One-button wipe: download history, cookies, built-in browser data, cached thumbnails and cache.
 *
 * Downloaded files are never touched. Settings, command templates and the app lock are kept.
 */
object QuickWipe {
    private const val TAG = "QuickWipe"

    enum class Result {
        DONE,
        DOWNLOADS_RUNNING,
    }

    suspend fun wipe(context: Context): Result {
        val appContext = context.applicationContext

        // Files and database first, while no download can start (yt-dlp uses the cache folder).
        val wiped =
            withContext(Dispatchers.IO) {
                YtdlpUpdateManager.runWhenNoDownloads {
                    runBlocking {
                        runCatching { DatabaseUtil.deleteAllHistory() }
                            .onFailure { Log.e(TAG, "history", it) }
                        runCatching { DatabaseUtil.deleteAllCookieProfiles() }
                            .onFailure { Log.e(TAG, "cookie profiles", it) }
                    }
                    // Thumbnail cache: cleared through its own API, not by deleting its folder.
                    clearThumbnailCache(appContext)
                    clearFolderContents(appContext.cacheDir, keep = THUMBNAIL_CACHE_FOLDER)
                    appContext.externalCacheDir?.let { clearFolderContents(it) }
                    // Leftover temporary download files, wherever they are (spec 4c).
                    TempStorage.clearAll(appContext)
                    // Leave an empty cookies file, as the app does on a fresh start.
                    runCatching {
                        DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                            FileUtil.writeContentToFile(it, appContext.getCookiesFile())
                        }
                    }
                }
            }
        if (!wiped) return Result.DOWNLOADS_RUNNING

        // Built-in browser data and in-memory thumbnails: these must be cleared on the main thread.
        withContext(Dispatchers.Main) {
            runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
                .onFailure { Log.e(TAG, "browser cookies", it) }
            runCatching { WebStorage.getInstance().deleteAllData() }
                .onFailure { Log.e(TAG, "browser storage", it) }
            runCatching {
                    @Suppress("DEPRECATION")
                    WebViewDatabase.getInstance(appContext).run {
                        clearHttpAuthUsernamePassword()
                        clearFormData()
                    }
                }
                .onFailure { Log.e(TAG, "browser database", it) }
            runCatching { appContext.imageLoader.memoryCache?.clear() }
            // Notifications can show video titles.
            runCatching { NotificationUtil.cancelAllNotifications() }
        }
        return Result.DONE
    }

    // Coil's default disk cache folder inside the cache directory.
    private const val THUMBNAIL_CACHE_FOLDER = "image_cache"

    @OptIn(ExperimentalCoilApi::class)
    private fun clearThumbnailCache(context: Context) {
        runCatching { context.imageLoader.diskCache?.clear() }
            .onFailure { Log.e(TAG, "thumbnail cache", it) }
    }

    private fun clearFolderContents(folder: File, keep: String? = null) {
        folder.listFiles()?.filter { it.name != keep }?.forEach { child ->
            runCatching { child.deleteRecursively() }
                .onFailure { Log.w(TAG, "couldn't delete ${child.name}", it) }
        }
    }
}
