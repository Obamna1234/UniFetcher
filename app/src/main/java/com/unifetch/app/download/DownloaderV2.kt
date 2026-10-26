package com.unifetch.app.download

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.download.Task.DownloadState
import com.unifetch.app.download.Task.DownloadState.Canceled
import com.unifetch.app.download.Task.DownloadState.Completed
import com.unifetch.app.download.Task.DownloadState.Error
import com.unifetch.app.download.Task.DownloadState.FetchingInfo
import com.unifetch.app.download.Task.DownloadState.Idle
import com.unifetch.app.download.Task.DownloadState.ReadyWithInfo
import com.unifetch.app.download.Task.DownloadState.Running
import com.unifetch.app.download.Task.RestartableAction.Download
import com.unifetch.app.download.Task.RestartableAction.FetchInfo
import com.unifetch.app.download.Task.TypeInfo
import com.unifetch.app.util.SiteSessions
import com.unifetch.app.util.StorageGuard
import com.unifetch.app.util.TempStorage
import java.io.IOException
import com.unifetch.app.util.DownloadUtil
import com.unifetch.app.util.FileUtil
import com.unifetch.app.util.NotificationUtil
import com.unifetch.app.util.PreferenceUtil
import com.unifetch.app.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "DownloaderV2"

private const val MAX_CONCURRENCY = 3

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap().keys.find { it.id == taskId }?.let { cancel(it) } ?: false
    }

    fun restart(task: Task)

    /** Enqueue a [Task] with an empty [Task.State] */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean

    // ---- Bulk actions for the download list's ⋮ menu ---------------------------------------

    private fun tasksWhere(predicate: (DownloadState) -> Boolean): List<Task> =
        getTaskStateMap().toMap().filter { (_, state) -> predicate(state.downloadState) }.keys.toList()

    /**
     * Stops every waiting and running download. They stay in the list and can be resumed.
     *
     * @return how many were stopped
     */
    fun cancelAll(): Int {
        var stopped = 0
        // Waiting ones first, so the queue can't start them while running ones are stopped.
        // Repeat a couple of times in case one started in between.
        repeat(3) {
            val waiting = tasksWhere { it == Idle || it == ReadyWithInfo }
            val running = tasksWhere { it is DownloadState.Cancelable }
            if (waiting.isEmpty() && running.isEmpty()) return stopped
            stopped += waiting.count { cancel(it) }
            stopped += running.count { cancel(it) }
        }
        return stopped
    }

    /** Puts every failed download back in the queue. @return how many */
    fun retryFailed(): Int =
        tasksWhere { it is Error }.count { task -> runCatching { restart(task) }.isSuccess }

    /** Resumes every stopped download. @return how many */
    fun resumeCanceled(): Int =
        tasksWhere { it is Canceled }.count { task -> runCatching { restart(task) }.isSuccess }

    /** Removes finished downloads from this list. Files and download history are kept. */
    fun clearFinished(): Int = tasksWhere { it is Completed }.count { remove(it) }

    /** Removes failed and stopped downloads from this list. Nothing on the phone is deleted. */
    fun clearFailedAndCanceled(): Int =
        tasksWhere { it is Error || it is Canceled }.count { remove(it) }
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean {
        return false
    }

    override fun restart(task: Task) {}

    override fun enqueue(task: Task) {}

    override fun enqueue(task: Task, state: Task.State) {}

    override fun remove(task: Task): Boolean {
        return true
    }
}

/**
 * TODO:
 *     - Notification
 *     - Custom commands
 *     - States for ViewModels
 */
@OptIn(FlowPreview::class)
class DownloaderV2Impl(private val appContext: Context) : DownloaderV2, KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskStateMap = mutableStateMapOf<Task, Task.State>()
    private val snapshotFlow = snapshotFlow { taskStateMap.toMap() }

    init {
        scope.launch(Dispatchers.Default) {
            snapshotFlow
                .onEach { doYourWork() }
                .map { it.countRunning() }
                .distinctUntilChanged()
                .collect { if (it > 0) App.startService() else App.stopService() }
        }

        // While paused for storage, check every 30 seconds and carry on once space is freed.
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(30_000)
                if (StorageGuard.paused.value) doYourWork()
            }
        }

        scope.launch(Dispatchers.IO) {
            // don't write before we read
            enqueueFromBackup()

            snapshotFlow
                .map { it.filter { it.value.downloadState !is Completed } }
                .distinctUntilChanged()
                .collect {
                    it.forEach { Log.d(TAG, it.value.viewState.title) }
                    PreferenceUtil.encodeTaskListBackup(it)
                }
        }
    }

    private fun enqueueFromBackup() {
        val taskList =
            PreferenceUtil.decodeTaskListBackup()
                .filter { it.value.downloadState !is Completed }
                .mapValues { (_, state) ->
                    val preState = state.downloadState
                    val downloadState =
                        when (preState) {
                            is FetchingInfo,
                            Idle -> {
                                Canceled(action = FetchInfo)
                            }
                            is Running -> {
                                Canceled(action = Download, progress = preState.progress)
                            }

                            ReadyWithInfo -> {
                                Canceled(action = Download, progress = null)
                            }
                            else -> {
                                preState
                            }
                        }
                    state.copy(downloadState = downloadState)
                }
        taskList.forEach(::enqueue)
    }

    private fun Map<Task, Task.State>.countRunning(): Int = count { (_, state) ->
        state.downloadState is Running || state.downloadState is FetchingInfo
    }

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return taskStateMap
    }

    override fun enqueue(task: Task) {
        taskStateMap +=
            task to Task.State(Idle, null, Task.ViewState(url = task.url, title = task.url))
    }

    override fun enqueue(task: Task, state: Task.State) {
        taskStateMap += task to state
    }

    /**
     * Noted the caller is responsible for stopping the [task] before removing it
     *
     * @return true if the task was removed
     */
    override fun remove(task: Task): Boolean {
        if (taskStateMap.contains(task)) {
            val videoId = taskStateMap[task]?.videoInfo?.id
            taskStateMap.remove(task)
            // Removed for good: delete its leftover temporary files (spec 4c).
            scope.launch(Dispatchers.IO) { TempStorage.deleteTaskTemp(videoId) }
            return true
        }
        return false
    }

    override fun cancel(task: Task): Boolean = task.cancelImpl()

    override fun restart(task: Task) {
        task.restartImpl()
    }

    private var Task.state: Task.State
        get() = taskStateMap[this]!!
        set(value) {
            taskStateMap[this] = value
        }

    private var Task.downloadState: DownloadState
        get() = state.downloadState
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(downloadState = value)
        }

    private var Task.info: VideoInfo?
        get() = state.videoInfo
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(videoInfo = value)
        }

    private var Task.viewState: Task.ViewState
        get() = state.viewState
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(viewState = value)
        }

    private val Task.notificationId: Int
        get() = id.hashCode()

    /** Processes pending tasks, prioritizing downloads. */
    @Synchronized
    private fun doYourWork() {
        if (taskStateMap.countRunning() >= MAX_CONCURRENCY) return
        val hasWaiting =
            taskStateMap.values.any { it.downloadState == ReadyWithInfo || it.downloadState == Idle }
        // Nearly out of space: don't start anything new (the storage check re-runs this).
        if (hasWaiting && !StorageGuard.canStartDownload()) return

        taskStateMap.entries
            .sortedBy { (_, state) -> state.downloadState }
            .firstOrNull { (_, state) ->
                state.downloadState == ReadyWithInfo || state.downloadState == Idle
            }
            ?.let { (task, state) ->
                when (state.downloadState) {
                    Idle -> task.prepare()
                    ReadyWithInfo -> task.download()
                    else -> {
                        throw IllegalStateException()
                    }
                }
            }
    }

    private fun Task.prepare() {
        check(downloadState == Idle)
        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        check(downloadState == Idle)
        val task = this
        val taskInfo = task.type
        val playlistIndex = if (taskInfo is TypeInfo.Playlist) taskInfo.index else null
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.fetchVideoInfoFromUrl(
                        url = url,
                        playlistIndex = playlistIndex,
                        preferences = preferences,
                        taskKey = id,
                    )
                    .onSuccess {
                        info = it
                        downloadState = ReadyWithInfo
                        viewState = Task.ViewState.fromVideoInfo(it)
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        task.downloadState = Error(throwable = throwable, action = FetchInfo)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = FetchingInfo(job = job, taskId = id) }
    }

    private fun Task.download() {
        check(downloadState == ReadyWithInfo && info != null)
        if (type is TypeInfo.CustomCommand) {
            execute()
            return
        }
        // Spec 4c: when the size is known, check there's room for the temporary files first.
        StorageGuard.checkRoomFor(info!!)?.let { message ->
            downloadState = Error(throwable = IOException(message), action = Download)
            return
        }
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.downloadVideo(
                        videoInfo = info,
                        taskId = id,
                        downloadPreferences = preferences,
                        progressCallback = { progressPercentage, _, text ->
                            val progress = progressPercentage / 100f
                            when (val preState = downloadState) {
                                is Running -> {
                                    // A small cookie mark shows when the download uses cookies.
                                    downloadState =
                                        preState.copy(
                                            progress = progress,
                                            progressText =
                                                if (SiteSessions.isUsingCookies(id)) "🍪 $text"
                                                else text,
                                        )
                                    NotificationUtil.notifyProgress(
                                        notificationId = notificationId,
                                        progress = progressPercentage.toInt(),
                                        text = text,
                                        title = viewState.title,
                                        taskId = id,
                                    )
                                }
                                else -> {}
                            }
                        },
                    )
                    .onSuccess { pathList ->
                        downloadState = Completed(pathList.firstOrNull())

                        val text =
                            appContext.getString(
                                if (pathList.isEmpty()) R.string.status_completed
                                else R.string.download_finish_notification
                            )
                        FileUtil.createIntentForOpeningFile(pathList.firstOrNull()).run {
                            NotificationUtil.finishNotification(
                                notificationId,
                                title = viewState.title,
                                text = text,
                                intent =
                                    if (this != null)
                                        PendingIntent.getActivity(
                                            appContext,
                                            0,
                                            this,
                                            PendingIntent.FLAG_IMMUTABLE,
                                        )
                                    else null,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.fetch_info_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = Running(job = job, taskId = id) }
    }

    private fun Task.cancelImpl(): Boolean {
        when (val preState = downloadState) {
            is DownloadState.Cancelable -> {
                val res = YoutubeDL.destroyProcessById(preState.taskId)
                if (res) {
                    preState.job.cancel()
                    val progress = if (preState is Running) preState.progress else null
                    NotificationUtil.cancelNotification(notificationId)
                    downloadState =
                        DownloadState.Canceled(action = preState.action, progress = progress)
                }
                return res
            }
            Idle -> {
                downloadState = DownloadState.Canceled(action = FetchInfo)
            }
            ReadyWithInfo -> {
                downloadState = DownloadState.Canceled(action = Download)
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun Task.restartImpl() {
        when (val preState = downloadState) {
            is DownloadState.Restartable -> {
                downloadState =
                    when (preState.action) {
                        Download -> ReadyWithInfo
                        FetchInfo -> Idle
                    }
            }
            else -> {
                throw IllegalStateException()
            }
        }
    }

    /**
     * Execute a custom command task
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        check(downloadState == Idle)
        check(type is TypeInfo.CustomCommand)
        val template = type.template
        scope
            .launch {
                DownloadUtil.executeCustomCommandTask(url, id, template, preferences) {
                        progressPercentage,
                        _,
                        text ->
                        val progress = progressPercentage / 100f
                        when (val preState = downloadState) {
                            is Running -> {
                                downloadState =
                                    preState.copy(progress = progress, progressText = text)
                                NotificationUtil.makeNotificationForCustomCommand(
                                    notificationId = notificationId,
                                    taskId = id,
                                    progress = progressPercentage.toInt(),
                                    templateName = template.name,
                                    taskUrl = url,
                                    text = text,
                                )
                            }
                            else -> {}
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.fetch_info_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
                    .onSuccess {
                        downloadState = Completed(null)

                        val text = appContext.getString(R.string.status_completed)

                        NotificationUtil.finishNotification(
                            notificationId = notificationId,
                            title = viewState.title,
                            text = text,
                            intent = null,
                        )
                    }
            }
            .also { downloadState = Running(job = it, taskId = id) }
    }
}
