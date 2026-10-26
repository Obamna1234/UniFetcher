package com.unifetch.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.IOException
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.unifetch.app.App.Companion.context
import com.unifetch.app.R
import com.unifetch.app.util.PreferenceUtil.getString
import java.io.File

const val AUDIO_REGEX = "(mp3|aac|opus|m4a)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".UniFetch"

object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) =
        path
            .runCatching {
                createIntentForOpeningFile(this)?.run { context.startActivity(this) }
                    ?: throw Exception()
            }
            .onFailure { onFailureCallback(it) }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? =
        createIntentForFile(path)?.let {
            it.apply {
                action = (Intent.ACTION_VIEW)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            if (!File(path).delete()) DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete()
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(title: String, downloadDir: String): List<String> =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile && it.absolutePath.contains(title) }
            .map { it.absolutePath }
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
                removeAll {
                    it.contains(Regex(THUMBNAIL_REGEX)) || it.contains(Regex(SUBTITLE_REGEX))
                }
            }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    /**
     * Copies finished downloads from [tempPath] to the SD card or USB drive folder chosen in
     * settings, deleting each temporary file once its copy is complete.
     *
     * Never loses a download and never falls back to another storage on its own (spec 4c): if
     * the drive is missing, full or not writable, the files stay in [tempPath] and this fails
     * with a clear message. Retrying the download then finds the finished file and copies it.
     */
    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> {
        val saved = mutableListOf<String>()
        val notCopied = mutableListOf<File>()

        val destDir =
            runCatching {
                    Uri.parse(sdcardUri).run {
                        DocumentsContract.buildDocumentUriUsingTree(
                            this,
                            DocumentsContract.getTreeDocumentId(this),
                        )
                    }
                }
                .onFailure { Log.w(TAG, "SD card / USB folder is not usable", it) }
                .getOrNull()

        tempPath.walkTopDown().filter { it.isFile }.forEach { file ->
            val copied = destDir?.let { copyToTree(file, it) }
            if (copied != null) {
                saved += copied.toString()
                file.delete()
            } else {
                notCopied += file
            }
        }

        if (notCopied.isNotEmpty()) {
            return Result.failure(
                IOException(context.getString(R.string.sdcard_copy_failed_kept))
            )
        }

        tempPath.deleteRecursively()
        return Result.success(saved)
    }

    /** Copies [file] into the folder [destDir]; returns the new file's address, or null on failure. */
    private fun copyToTree(file: File, destDir: Uri): Uri? {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
        var destUri: Uri? = null
        return try {
            destUri = DocumentsContract.createDocument(resolver, destDir, mimeType, file.name)
                ?: return null
            val output = resolver.openOutputStream(destUri) ?: throw IOException("can't write")
            file.inputStream().use { input -> output.use { input.copyTo(it) } }
            destUri
        } catch (e: Exception) {
            Log.w(TAG, "copy to SD card / USB drive failed: ${file.name}", e)
            // Remove a half-written copy so it isn't mistaken for a complete file.
            destUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            null
        }
    }


    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }


    /** UniFetcher's private "tmp" folder on the drive chosen in settings, or null if unavailable. */
    fun Context.getDriveWorkDir(): File? {
        val treeUri = SDCARD_URI.getString()
        if (treeUri.isEmpty()) return null
        return runCatching {
                // e.g. "1A2B-3C4D:Videos" -> "1A2B-3C4D". "primary" is the phone's own storage.
                val volumeId =
                    DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).substringBefore(':')
                if (volumeId.isEmpty() || volumeId == "primary") null
                else
                    getExternalFilesDirs(null)
                        .filterNotNull()
                        .firstOrNull { it.absolutePath.contains("/$volumeId/") }
                        ?.resolve("tmp")
                        ?.apply { mkdirs() }
                        ?.takeIf { it.isDirectory && it.canWrite() }
            }
            .onFailure { Log.w(TAG, "drive work folder unavailable", it) }
            .getOrNull()
    }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UniFetcher")
            .also { it.mkdir() }

    internal fun getExternalPrivateDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PRIVATE_DIRECTORY_SUFFIX,
        )

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast(R.string.directory_not_on_phone)
            return getExternalDownloadDirectory().absolutePath
        }
        val last: String = path.split("primary:").last()
        return Environment.getExternalStorageDirectory().absolutePath + "/$last"
    }

    private const val TAG = "FileUtil"
}
