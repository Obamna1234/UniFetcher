package com.unifetch.app.util

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.core.content.edit
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.PreferenceUtil.getInt
import com.unifetch.app.util.PreferenceUtil.getLong
import com.unifetch.app.util.PreferenceUtil.getString
import com.unifetch.app.util.PreferenceUtil.updateLong
import com.unifetch.app.util.PreferenceUtil.updateString
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps yt-dlp up to date. Every update path goes through here: app launch, before fetching
 * video info, the background check, the automatic retry after an extractor error, and the
 * "check now" button.
 *
 * Updates only ever come from the official yt-dlp GitHub releases (the same addresses the
 * youtubedl-android library uses for each channel).
 *
 * yt-dlp keeps reading its own file while it runs, so the file must not be replaced while any
 * yt-dlp process is running. Every yt-dlp run holds a read lock and an update takes the write
 * lock: updates wait for running downloads, and new downloads wait for the (short) update.
 */
object YtdlpUpdateManager {
    private const val TAG = "YtdlpUpdateManager"

    // Library internals: where youtubedl-android keeps yt-dlp and records its version.
    private const val LIBRARY_PREFS = "youtubedl-android"
    private const val LIBRARY_VERSION_KEY = "dlpVersion"
    private const val LIBRARY_VERSION_NAME_KEY = "dlpVersionName"

    /** How long the retry and the "check now" button wait for running downloads to finish. */
    private val WAIT_FOR_DOWNLOADS_MS = TimeUnit.MINUTES.toMillis(3)

    enum class Trigger {
        LAUNCH,
        BEFORE_FETCH,
        BACKGROUND,
        RETRY,
        MANUAL,
    }

    enum class Result {
        UP_TO_DATE,
        UPDATED,
        UPDATE_AVAILABLE,
        SKIPPED_VERSION,
        BUSY,
        DISABLED,
        FAILED,
    }

    private val runLock = ReentrantReadWriteLock(true)

    /** Only one check, update or rollback at a time. */
    private val checkLock = ReentrantLock()

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class LatestRelease(@SerialName("tag_name") val tagName: String)

    private val context: Context
        get() = App.context

    fun mode(): Int = YT_DLP_UPDATE_MODE.getInt()

    private fun updateChannel(): YoutubeDL.UpdateChannel =
        when (YT_DLP_UPDATE_CHANNEL.getInt()) {
            YT_DLP_STABLE -> YoutubeDL.UpdateChannel.STABLE
            YT_DLP_MASTER -> YoutubeDL.UpdateChannel.MASTER
            else -> YoutubeDL.UpdateChannel.NIGHTLY
        }

    /** Runs a yt-dlp process. A yt-dlp update waits until it finishes. */
    fun <T> runYtdlp(block: () -> T): T {
        runLock.readLock().lock()
        try {
            return block()
        } finally {
            runLock.readLock().unlock()
        }
    }

    /**
     * Runs a yt-dlp process. If it fails with an error that a newer yt-dlp could fix, updates
     * yt-dlp (only in "Update automatically" mode) and tries once more.
     */
    fun <T> runYtdlpWithAutoRetry(onStep: (String) -> Unit = {}, block: () -> T): T {
        try {
            return runYtdlp(block)
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            if (!YtdlpUpdatePolicy.shouldUpdateAndRetry(e.message)) throw e
            onStep("yt-dlp couldn't read the site: checked for a yt-dlp update")
            val failedAt = System.currentTimeMillis()
            val result =
                runCatching { checkAndUpdate(Trigger.RETRY, failedAt = failedAt) }
                    .getOrDefault(Result.FAILED)
            if (result != Result.UPDATED) {
                onStep("No yt-dlp update was installed (${result.name.lowercase()})")
                throw e
            }
            onStep("Installed a yt-dlp update and retried once")
            Log.i(TAG, "yt-dlp was updated after an extractor error; retrying once")
            return runYtdlp(block)
        }
    }

    /**
     * Runs [block] only if no yt-dlp process is running, keeping new ones from starting until it
     * finishes. Returns false (without running it) if downloads are running.
     */
    fun runWhenNoDownloads(block: () -> Unit): Boolean {
        val writeLock = runLock.writeLock()
        if (!writeLock.tryLock()) return false
        try {
            block()
        } finally {
            writeLock.unlock()
        }
        return true
    }

    /** The "before every fetch" check. Never throws and never blocks on running downloads. */
    fun checkBeforeFetch() {
        runCatching { checkAndUpdate(Trigger.BEFORE_FETCH) }
            .onFailure { Log.w(TAG, "check before fetch failed", it) }
    }

    /**
     * Checks GitHub for a newer yt-dlp on the chosen channel and installs it, following the
     * user's update setting. Blocking: call from a background thread.
     *
     * @param failedAt for [Trigger.RETRY]: when the failed attempt happened. If another task
     *   already updated yt-dlp after that, the caller should simply retry.
     */
    fun checkAndUpdate(trigger: Trigger, failedAt: Long = 0L): Result {
        val waits = trigger == Trigger.MANUAL || trigger == Trigger.RETRY
        // Quick checks (launch, before fetch, background) skip if another check is running.
        if (waits) checkLock.lock() else if (!checkLock.tryLock()) return Result.BUSY
        try {
            return checkAndUpdateLocked(trigger, failedAt)
        } finally {
            checkLock.unlock()
        }
    }

    private fun checkAndUpdateLocked(trigger: Trigger, failedAt: Long): Result {
        val mode = mode()
        val manual = trigger == Trigger.MANUAL
        if (!manual && mode == YT_DLP_MODE_OFF) return Result.DISABLED
        if (trigger == Trigger.RETRY && mode != YT_DLP_MODE_AUTO) return Result.DISABLED
        if (failedAt > 0 && YT_DLP_UPDATE_TIME.getLong() > failedAt) return Result.UPDATED

        val now = System.currentTimeMillis()
        val lastCheck = YT_DLP_LAST_CHECK.getLong()
        when (trigger) {
            Trigger.BEFORE_FETCH ->
                if (
                    !YtdlpUpdatePolicy.isCheckDue(
                        lastCheck,
                        now,
                        YtdlpUpdatePolicy.FETCH_CHECK_INTERVAL,
                    )
                )
                    return Result.UP_TO_DATE
            Trigger.RETRY ->
                if (
                    !YtdlpUpdatePolicy.isCheckDue(
                        lastCheck,
                        now,
                        YtdlpUpdatePolicy.RETRY_CHECK_COOLDOWN,
                    )
                )
                    return Result.UP_TO_DATE
            else -> Unit
        }

        val latest = fetchLatestTag(updateChannel()) ?: return Result.FAILED
        YT_DLP_LAST_CHECK.updateLong(now)

        if (latest == YoutubeDL.getInstance().version(context)) {
            YT_DLP_PENDING_TAG.updateString("")
            return Result.UP_TO_DATE
        }
        if (!manual && latest == YT_DLP_SKIPPED_TAG.getString()) return Result.SKIPPED_VERSION
        if (!manual && mode == YT_DLP_MODE_ASK) {
            YT_DLP_PENDING_TAG.updateString(latest)
            return Result.UPDATE_AVAILABLE
        }

        val wait =
            if (manual || trigger == Trigger.RETRY) WAIT_FOR_DOWNLOADS_MS else 0L
        return install(waitMillis = wait)
    }

    private fun install(waitMillis: Long): Result {
        val writeLock = runLock.writeLock()
        val locked =
            if (waitMillis > 0) writeLock.tryLock(waitMillis, TimeUnit.MILLISECONDS)
            else writeLock.tryLock()
        if (!locked) {
            Log.i(TAG, "downloads are running; yt-dlp update postponed")
            return Result.BUSY
        }
        try {
            runCatching { backUpCurrentVersion() }
                .onFailure { Log.w(TAG, "couldn't back up yt-dlp; rollback unavailable", it) }

            val status = YoutubeDL.getInstance().updateYoutubeDL(context, updateChannel())
            if (status != YoutubeDL.UpdateStatus.DONE) return Result.UP_TO_DATE

            YoutubeDL.getInstance().version(context)?.let { YT_DLP_VERSION.updateString(it) }
            YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
            YT_DLP_PENDING_TAG.updateString("")
            YT_DLP_SKIPPED_TAG.updateString("")
            Log.i(TAG, "yt-dlp updated to ${YT_DLP_VERSION.getString()}")
            return Result.UPDATED
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp update failed", e)
            return Result.FAILED
        } finally {
            writeLock.unlock()
        }
    }

    private fun fetchLatestTag(channel: YoutubeDL.UpdateChannel): String? {
        val request =
            Request.Builder()
                .url(channel.apiUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    json.decodeFromString<LatestRelease>(response.body.string()).tagName
                } else {
                    Log.w(TAG, "GitHub answered ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "couldn't reach GitHub", e)
            null
        }
    }

    // ---- Rollback -------------------------------------------------------------------------

    private fun installedFile(): File =
        File(
            File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName),
            YoutubeDL.ytdlpBin,
        )

    private fun backupFile(): File = File(File(context.noBackupFilesDir, "ytdlp-backup"), "yt-dlp")

    private fun backUpCurrentVersion() {
        val current = installedFile()
        if (!current.exists()) return
        current.copyTo(backupFile(), overwrite = true)
        YT_DLP_BACKUP_TAG.updateString(YoutubeDL.getInstance().version(context) ?: "")
        YT_DLP_BACKUP_VERSION.updateString(YT_DLP_VERSION.getString())
    }

    fun canRollBack(): Boolean = backupFile().exists()

    /** The version a rollback would go back to, for display. */
    fun rollbackVersionName(): String =
        YT_DLP_BACKUP_VERSION.getString().ifEmpty { context.getString(R.string.ytdlp_builtin) }

    /**
     * Restores the previous yt-dlp and skips the current version, so automatic updates don't
     * reinstall it. A newer version than the skipped one is installed normally.
     */
    fun rollBack(): Boolean {
        checkLock.lock()
        try {
            return rollBackLocked()
        } finally {
            checkLock.unlock()
        }
    }

    private fun rollBackLocked(): Boolean {
        val backup = backupFile()
        if (!backup.exists()) return false
        val writeLock = runLock.writeLock()
        if (!writeLock.tryLock(WAIT_FOR_DOWNLOADS_MS, TimeUnit.MILLISECONDS)) return false
        try {
            val badVersion = YoutubeDL.getInstance().version(context)
            backup.copyTo(installedFile(), overwrite = true)

            val previousTag = YT_DLP_BACKUP_TAG.getString()
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit {
                if (previousTag.isEmpty()) {
                    remove(LIBRARY_VERSION_KEY)
                    remove(LIBRARY_VERSION_NAME_KEY)
                } else {
                    putString(LIBRARY_VERSION_KEY, previousTag)
                    putString(LIBRARY_VERSION_NAME_KEY, previousTag)
                }
            }
            YT_DLP_VERSION.updateString(YT_DLP_BACKUP_VERSION.getString())
            badVersion?.let { YT_DLP_SKIPPED_TAG.updateString(it) }
            YT_DLP_PENDING_TAG.updateString("")

            backup.delete()
            YT_DLP_BACKUP_TAG.updateString("")
            YT_DLP_BACKUP_VERSION.updateString("")
            Log.i(TAG, "rolled back yt-dlp; skipping $badVersion")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "rollback failed", e)
            return false
        } finally {
            writeLock.unlock()
        }
    }

    // ---- Display ----------------------------------------------------------------------------

    /** A pending update found in "Ask me first" mode, or null. */
    fun pendingUpdate(): String? = YT_DLP_PENDING_TAG.getString().ifEmpty { null }

    /** Clears a pending update after the user says "Not now"; it's offered again next check. */
    fun dismissPendingUpdate() = YT_DLP_PENDING_TAG.updateString("")

    /** Called when the user changes the update channel. */
    fun onChannelChanged() {
        YT_DLP_SKIPPED_TAG.updateString("")
        YT_DLP_PENDING_TAG.updateString("")
        YT_DLP_LAST_CHECK.updateLong(0L)
    }

    fun installedVersionName(): String =
        YoutubeDL.getInstance().version(context)
            ?: YT_DLP_VERSION.getString().ifEmpty { context.getString(R.string.ytdlp_builtin) }

    fun lastCheckedText(): String {
        val last = YT_DLP_LAST_CHECK.getLong()
        return if (last <= 0L) context.getString(R.string.ytdlp_never_checked)
        else
            context.getString(
                R.string.ytdlp_last_checked,
                DateUtils.getRelativeTimeSpanString(
                    last,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ),
            )
    }

    /** e.g. "2026.10.20.232849 · Checked 5 minutes ago" */
    fun describe(): String = "${installedVersionName()} · ${lastCheckedText()}"
}
