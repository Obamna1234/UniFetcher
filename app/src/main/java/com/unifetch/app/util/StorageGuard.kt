package com.unifetch.app.util

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.PreferenceUtil.getLong
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses the download queue instead of letting downloads fail when temporary files can't be
 * written (spec 4c): the temporary location is nearly full, the SD card / USB drive was removed,
 * or the phone doesn't give UniFetcher a folder on the drive. Downloads already running carry
 * on; the queue continues by itself once the problem is fixed.
 */
object StorageGuard {
    private const val TAG = "StorageGuard"

    private const val GB = 1024L * 1024 * 1024

    /** Choices for "Pause downloads when free space is below". 0 means never pause. */
    val LIMIT_OPTIONS = listOf(2 * GB, GB, GB / 2, 0L)

    /** Why the queue is paused. */
    sealed interface Reason {
        data class LowSpace(val freeBytes: Long, val onRemovable: Boolean) : Reason

        /** The SD card or USB drive (for temporary files or the download folder) isn't connected. */
        data object DriveRemoved : Reason

        /** Downloads go to a drive the phone doesn't give UniFetcher a folder on. */
        data object NoAppFolderOnDrive : Reason
    }

    private val mutableReason = MutableStateFlow<Reason?>(null)

    /** Why the queue is paused, or null if it isn't. */
    val reason: StateFlow<Reason?> = mutableReason.asStateFlow()

    private val mutablePaused = MutableStateFlow(false)

    /** True while the queue is paused. */
    val paused: StateFlow<Boolean> = mutablePaused.asStateFlow()

    fun minFreeBytes(): Long = STORAGE_MIN_FREE.getLong()

    /** Free space in [folder] (or the nearest folder above it that exists), or null if unknown. */
    fun freeBytesAt(folder: File?): Long? =
        runCatching {
                var f = folder
                while (f != null && !f.exists()) f = f.parentFile
                (f ?: Environment.getExternalStorageDirectory()).usableSpace
            }
            .onFailure { Log.w(TAG, "couldn't read free space", it) }
            .getOrNull()
            // 0 usually means the size couldn't be read, not that the storage is completely full.
            ?.takeIf { it > 0L }

    fun phoneFreeBytes(): Long? = freeBytesAt(Environment.getExternalStorageDirectory())

    /** Free space where temporary files currently go, or null if unknown or unavailable. */
    fun tempFreeBytes(context: Context = App.context): Long? =
        (TempStorage.resolve(context) as? TempStorage.Where.Ready)?.let { freeBytesAt(it.root) }

    private fun currentReason(context: Context = App.context): Reason? {
        if (TempStorage.downloadDriveMissing(context)) return Reason.DriveRemoved
        return when (val where = TempStorage.resolve(context)) {
            TempStorage.Where.DriveHasNoAppFolder -> Reason.NoAppFolderOnDrive
            TempStorage.Where.RemovableMissing -> Reason.DriveRemoved
            is TempStorage.Where.Ready -> {
                val limit = minFreeBytes()
                val free = if (limit > 0L) freeBytesAt(where.root) else null
                if (limit > 0L && free != null && free < limit)
                    Reason.LowSpace(free, where.onRemovable)
                else null
            }
        }
    }

    /**
     * True if a new download may start. Updates [reason], [paused] and the notification when the
     * answer changes.
     */
    fun canStartDownload(): Boolean {
        val now = currentReason()
        val before = mutableReason.value
        mutableReason.value = now
        mutablePaused.value = now != null
        if ((now == null) != (before == null) || (now != null && now::class != before?.let { it::class })) {
            if (now != null) {
                Log.i(TAG, "pausing the queue: $now")
                NotificationUtil.notifyStorageLow()
            } else {
                Log.i(TAG, "storage problem fixed: resuming the queue")
                NotificationUtil.cancelStorageLowNotification()
            }
        }
        return now == null
    }

    /**
     * Checks there's room for one download's temporary files, when its size is known.
     *
     * @return null if it fits (or the size is unknown), otherwise a message saying why not
     */
    fun checkRoomFor(info: VideoInfo, context: Context = App.context): String? {
        val parts =
            info.requestedFormats?.takeIf { it.isNotEmpty() }?.map { it.fileSize ?: it.fileSizeApprox }
        val size =
            (parts?.takeIf { p -> p.all { it != null } }?.sumOf { it ?: 0.0 }
                    ?: info.fileSize
                    ?: info.fileSizeApprox)
                ?.toLong()
                ?.takeIf { it > 0L } ?: return null
        // Separate video and audio are joined into a new file, which briefly needs about twice
        // the space. Otherwise allow 10% extra.
        val needed = if ((parts?.size ?: 0) > 1) size * 2 else size + size / 10
        val free = tempFreeBytes(context) ?: return null
        if (free >= needed) return null
        return context.getString(
            R.string.storage_not_enough_for_video,
            formatSize(context, needed),
            formatSize(context, free),
        )
    }

    fun formatSize(context: Context, bytes: Long?): String =
        if (bytes == null) context.getString(R.string.unknown)
        else Formatter.formatShortFileSize(context, bytes)

    fun limitText(context: Context, limit: Long): String =
        if (limit <= 0L) context.getString(R.string.storage_limit_off)
        else formatSize(context, limit)

    fun titleFor(context: Context, reason: Reason?): String =
        context.getString(
            when (reason) {
                Reason.DriveRemoved -> R.string.storage_pause_removed_title
                Reason.NoAppFolderOnDrive -> R.string.storage_pause_no_app_folder_title
                else -> R.string.storage_low_title
            }
        )

    fun messageFor(context: Context, reason: Reason?): String =
        when (reason) {
            Reason.DriveRemoved -> context.getString(R.string.storage_pause_removed)
            Reason.NoAppFolderOnDrive -> context.getString(R.string.storage_pause_no_app_folder)
            is Reason.LowSpace ->
                context.getString(
                    R.string.storage_low_text,
                    formatSize(context, reason.freeBytes),
                    context.getString(
                        if (reason.onRemovable) R.string.storage_location_drive
                        else R.string.storage_location_phone
                    ),
                    limitText(context, minFreeBytes()),
                )
            null -> ""
        }

    /** Explains where temporary files go right now, with free space. */
    fun statusText(context: Context): String =
        when (val where = TempStorage.resolve(context)) {
            is TempStorage.Where.Ready ->
                context.getString(
                    if (where.onRemovable) R.string.temp_status_removable
                    else R.string.temp_status_internal,
                    formatSize(context, freeBytesAt(where.root)),
                )
            TempStorage.Where.DriveHasNoAppFolder -> context.getString(R.string.storage_pause_no_app_folder)
            TempStorage.Where.RemovableMissing -> context.getString(R.string.storage_pause_removed)
        }
}
