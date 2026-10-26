package com.unifetch.app.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.getSystemService
import com.unifetch.app.App
import com.unifetch.app.util.FileUtil.getDriveWorkDir
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.getInt
import com.unifetch.app.util.PreferenceUtil.getString
import java.io.File

/**
 * Where yt-dlp keeps temporary and partial files (unfinished video and audio pieces, then the
 * merge) while downloading. Spec section 4c.
 *
 * Each download gets its own folder inside the chosen location, named after the video, so its
 * leftovers can be cleaned up and an interrupted download can continue from where it stopped.
 *
 * Never falls back to another storage on its own: if the chosen location isn't usable, the
 * queue pauses and says why (see [StorageGuard]).
 */
object TempStorage {
    private const val TAG = "TempStorage"

    /** Same storage as the download folder (default). */
    const val SAME_AS_DOWNLOADS = 0
    const val INTERNAL = 1
    /** UniFetcher's own folder on an SD card or USB drive. */
    const val REMOVABLE = 2

    sealed interface Where {
        /** Temporary files go in [root]. */
        data class Ready(val root: File, val onRemovable: Boolean) : Where

        /**
         * Downloads go to an SD card or USB drive that's plugged in, but this phone doesn't give
         * UniFetcher a folder on it, so temporary files can't go there.
         */
        data object DriveHasNoAppFolder : Where

        /** The SD card or USB drive needed is not connected. */
        data object RemovableMissing : Where
    }

    fun choice(): Int = TEMP_LOCATION.getInt()

    /** The phone's own temporary folder: Download/UniFetcher/tmp. */
    fun internalRoot(): File = FileUtil.getExternalTempDir()

    /** UniFetcher's own folders on SD cards / USB drives Android makes available to it. */
    fun removableAppDirs(context: Context = App.context): List<File> =
        runCatching {
                context
                    .getExternalFilesDirs(null)
                    .drop(1) // the first one is on the phone's own storage
                    .filterNotNull()
                    .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            }
            .getOrDefault(emptyList())

    /** The first SD card / USB drive temporary folder, or null if none is available. */
    fun removableRoot(context: Context = App.context): File? =
        removableAppDirs(context).firstOrNull()?.resolve("tmp")

    /** Volume ID of the drive chosen under "SD card or USB drive folder", if that's switched on. */
    fun selectedDriveVolumeId(): String? {
        if (!SDCARD_DOWNLOAD.getBoolean()) return null
        val treeUri = SDCARD_URI.getString().ifEmpty { return null }
        return runCatching {
                DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).substringBefore(':')
            }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** True if the storage volume with this ID (e.g. "1A2B-3C4D") is plugged in and ready. */
    fun isVolumeMounted(volumeId: String, context: Context = App.context): Boolean {
        if (volumeId == "primary") return true
        return runCatching {
                context.getSystemService<StorageManager>()?.storageVolumes?.any {
                    it.uuid.equals(volumeId, ignoreCase = true) &&
                        it.state == Environment.MEDIA_MOUNTED
                } ?: false
            }
            .onFailure { Log.w(TAG, "couldn't list storage volumes", it) }
            .getOrDefault(false)
    }

    /** True if downloads should end up on an SD card / USB drive and it isn't connected. */
    fun downloadDriveMissing(context: Context = App.context): Boolean =
        selectedDriveVolumeId()?.let { !isVolumeMounted(it, context) } ?: false

    fun resolve(context: Context = App.context): Where =
        when (choice()) {
            INTERNAL -> Where.Ready(internalRoot(), onRemovable = false)
            REMOVABLE ->
                removableRoot(context)?.let { Where.Ready(it.apply { mkdirs() }, onRemovable = true) }
                    ?: Where.RemovableMissing
            else -> {
                val volumeId = selectedDriveVolumeId()
                when {
                    // Downloads go to the phone's own storage.
                    volumeId == null || volumeId == "primary" ->
                        Where.Ready(internalRoot(), onRemovable = false)
                    else ->
                        context.getDriveWorkDir()?.let { Where.Ready(it, onRemovable = true) }
                            ?: if (isVolumeMounted(volumeId, context)) Where.DriveHasNoAppFolder
                            else Where.RemovableMissing
                }
            }
        }

    /** The temporary folder for one download, or null if the location isn't usable right now. */
    fun taskDir(videoId: String?, context: Context = App.context): File? {
        val root = (resolve(context) as? Where.Ready)?.root ?: return null
        return root.resolve(folderName(videoId)).apply { mkdirs() }
    }

    /** Every place UniFetcher may have left temporary files. */
    fun allRoots(context: Context = App.context): List<File> =
        listOf(internalRoot()) + removableAppDirs(context).map { it.resolve("tmp") }

    /** Deletes the temporary files of one download, wherever they are. */
    fun deleteTaskTemp(videoId: String?, context: Context = App.context) {
        if (videoId.isNullOrBlank()) return
        val name = folderName(videoId)
        allRoots(context).forEach { root ->
            File(root, name)
                .takeIf { it.exists() }
                ?.let { runCatching { it.deleteRecursively() } }
        }
    }

    /** Deletes all of UniFetcher's temporary files. Only call when nothing is downloading. */
    fun clearAll(context: Context = App.context): Int {
        var count = 0
        allRoots(context).forEach { root ->
            root.listFiles()?.filter { it.name != ".nomedia" }?.forEach {
                if (runCatching { it.deleteRecursively() }.getOrDefault(false)) count++
            }
        }
        return count
    }

    /** A safe folder name for a video ID (IDs can contain characters folders can't). */
    private fun folderName(videoId: String?): String =
        (videoId ?: "unknown").replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifEmpty { "unknown" }
}
