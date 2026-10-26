package com.unifetch.app.ui.page.downloadv2

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilledTonalButton
import com.unifetch.app.download.needsUser
import com.unifetch.app.util.SiteSessions
import com.unifetch.app.SiteCheckActivity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import com.unifetch.app.util.PreferenceUtil
import com.unifetch.app.util.StorageGuard
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifetch.app.R
import com.unifetch.app.download.DownloaderV2
import com.unifetch.app.download.Task
import com.unifetch.app.download.Task.DownloadState.Canceled
import com.unifetch.app.download.Task.DownloadState.Completed
import com.unifetch.app.download.Task.DownloadState.Error
import com.unifetch.app.download.Task.DownloadState.FetchingInfo
import com.unifetch.app.download.Task.DownloadState.Idle
import com.unifetch.app.download.Task.DownloadState.ReadyWithInfo
import com.unifetch.app.download.Task.DownloadState.Running
import com.unifetch.app.ui.common.HapticFeedback.slightHapticFeedback
import com.unifetch.app.ui.common.LocalDarkTheme
import com.unifetch.app.ui.common.LocalFixedColorRoles
import com.unifetch.app.ui.common.LocalWindowWidthState
import com.unifetch.app.ui.component.SealModalBottomSheet
import com.unifetch.app.ui.component.SelectionGroupDefaults
import com.unifetch.app.ui.component.SelectionGroupItem
import com.unifetch.app.ui.component.SelectionGroupRow
import com.unifetch.app.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.unifetch.app.ui.page.downloadv2.configure.Config
import com.unifetch.app.ui.page.downloadv2.configure.DownloadDialog
import com.unifetch.app.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.unifetch.app.ui.page.downloadv2.configure.FormatPage
import com.unifetch.app.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.unifetch.app.ui.page.downloadv2.configure.PreferencesMock
import com.unifetch.app.ui.svg.DynamicColorImageVectors
import com.unifetch.app.ui.svg.drawablevectors.download
import com.unifetch.app.ui.theme.SealTheme
import com.unifetch.app.util.DownloadUtil
import com.unifetch.app.util.FileUtil
import com.unifetch.app.util.getErrorReport
import com.unifetch.app.util.makeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "DownloadPageV2"

enum class Filter {
    All,
    Downloading,
    /** Waiting for the user: a "verify you are human" check or a login (spec 4a). */
    NeedsYou,
    Canceled,
    Finished;

    @Composable
    @ReadOnlyComposable
    fun label(): String =
        when (this) {
            All -> stringResource(R.string.all)
            Downloading -> stringResource(R.string.status_downloading)
            NeedsYou -> stringResource(R.string.needs_you)
            Canceled -> stringResource(R.string.status_canceled)
            Finished -> stringResource(R.string.status_completed)
        }

    fun predict(entry: Pair<Task, Task.State>): Boolean {
        if (this == All) return true
        val state = entry.second.downloadState
        return when (this) {
            Downloading -> {
                when (state) {
                    is FetchingInfo,
                    Idle,
                    ReadyWithInfo,
                    is Running -> true
                    else -> false
                }
            }
            NeedsYou -> state.needsUser()
            Canceled -> {
                (state is Error && !state.needsUser()) || state is Task.DownloadState.Canceled
            }
            Finished -> {
                state is Completed
            }
            else -> {
                true
            }
        }
    }
}

sealed interface UiAction {
    data class OpenFile(val filePath: String?) : UiAction

    data class ShareFile(val filePath: String?) : UiAction

    data class OpenThumbnailURL(val url: String) : UiAction

    data object CopyVideoURL : UiAction

    data class OpenVideoURL(val url: String) : UiAction

    data object Cancel : UiAction

    data object Delete : UiAction

    data object Resume : UiAction

    data class CopyErrorReport(val throwable: Throwable) : UiAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageV2(
    modifier: Modifier = Modifier,
    onMenuOpen: (() -> Unit) = {},
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    DownloadPageImplV2(
        modifier = modifier,
        taskDownloadStateMap = downloader.getTaskStateMap(),
        downloadCallback = {
            view.slightHapticFeedback()
            dialogViewModel.postAction(Action.ShowSheet())
        },
        onMenuOpen = onMenuOpen,
        onBulkAction = { action ->
            view.slightHapticFeedback()
            val count =
                when (action) {
                    BulkAction.CancelAll -> downloader.cancelAll()
                    BulkAction.ResumeCanceled -> downloader.resumeCanceled()
                    BulkAction.RetryFailed -> downloader.retryFailed()
                    BulkAction.ClearFinished -> downloader.clearFinished()
                    BulkAction.ClearFailed -> downloader.clearFailedAndCanceled()
                }
            context.makeToast(context.resources.getQuantityString(R.plurals.bulk_action_done, count, count))
        },
    ) { task, action ->
        view.slightHapticFeedback()
        when (action) {
            UiAction.Cancel -> downloader.cancel(task)
            UiAction.Delete -> downloader.remove(task)
            UiAction.Resume -> downloader.restart(task)
            is UiAction.CopyErrorReport -> {
                clipboardManager.setText(
                    AnnotatedString(getErrorReport(action.throwable, task.url))
                )
                context.makeToast(R.string.error_copied)
            }
            UiAction.CopyVideoURL -> {
                clipboardManager.setText(AnnotatedString(task.url))
                context.makeToast(R.string.link_copied)
            }
            is UiAction.OpenFile -> {
                action.filePath?.let {
                    FileUtil.openFile(path = it) { context.makeToast(R.string.file_unavailable) }
                }
            }
            is UiAction.OpenThumbnailURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.OpenVideoURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.ShareFile -> {
                val shareTitle = context.getString(R.string.share)
                FileUtil.createIntentForSharingFile(action.filePath)?.let {
                    context.startActivity(Intent.createChooser(it, shareTitle))
                }
            }
        }
    }

    var preferences by remember {
        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
    }
    val sheetValue by dialogViewModel.sheetValueFlow.collectAsStateWithLifecycle()
    val state by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()

    val selectionState = dialogViewModel.selectionStateFlow.collectAsStateWithLifecycle().value

    var showDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(sheetValue) {
        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
            showDialog = true
        } else {
            launch { sheetState.hide() }.invokeOnCompletion { showDialog = false }
        }
    }

    if (showDialog) {

        DownloadDialog(
            state = state,
            sheetState = sheetState,
            config = Config(),
            preferences = preferences,
            onPreferencesUpdate = { preferences = it },
            onActionPost = { dialogViewModel.postAction(it) },
        )
    }
    when (selectionState) {
        is DownloadDialogViewModel.SelectionState.FormatSelection ->
            FormatPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )

        is DownloadDialogViewModel.SelectionState.PlaylistSelection -> {
            PlaylistSelectionPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )
        }

        DownloadDialogViewModel.SelectionState.Idle -> {}
    }
}

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = calculateTopPadding() + other.calculateTopPadding(),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
        start =
            calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
    )
}

private const val HeaderSpacingDp = 28

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageImplV2(
    modifier: Modifier = Modifier,
    taskDownloadStateMap: SnapshotStateMap<Task, Task.State>,
    downloadCallback: () -> Unit = {},
    onMenuOpen: (() -> Unit) = {},
    onBulkAction: (BulkAction) -> Unit = {},
    onActionPost: (Task, UiAction) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(Filter.All) }
    val storagePaused by StorageGuard.paused.collectAsState()
    // Sites with downloads waiting for the user, e.g. "example.com" -> its waiting tasks.
    val needsYouSites by remember {
        derivedStateOf {
            taskDownloadStateMap
                .filter { (_, state) -> state.downloadState.needsUser() }
                .keys
                .groupBy { SiteSessions.siteOf(it.url) ?: it.url }
                .toList()
                .sortedByDescending { it.second.size }
        }
    }
    // "Disable preview" setting: hide thumbnails on download cards.
    val previewDisabled by PreferenceUtil.previewDisabled.collectAsState()
    val filteredMap by
        remember(activeFilter) {
            derivedStateOf { taskDownloadStateMap.filter { activeFilter.predict(it.toPair()) } }
        }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    val view = LocalView.current

    fun showActionSheet(task: Task) {
        view.slightHapticFeedback()
        scope.launch {
            selectedTask = task
            delay(50)
            sheetState.show()
        }
    }

    LaunchedEffect(selectedTask, taskDownloadStateMap.size) {
        if (!taskDownloadStateMap.contains(selectedTask)) {
            selectedTask == null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = { FABs(modifier = Modifier, downloadCallback = downloadCallback) },
    ) { windowInsetsPadding ->
        val lazyListState = rememberLazyGridState()
        val windowWidthSizeClass = LocalWindowWidthState.current
        val spacerHeight =
            with(LocalDensity.current) {
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) 0f
                else HeaderSpacingDp.dp.toPx()
            }
        var headerOffset by remember { mutableFloatStateOf(spacerHeight) }
        var isGridView by rememberSaveable { mutableStateOf(true) }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .then(
                        if (windowWidthSizeClass != WindowWidthSizeClass.Compact) Modifier
                        else
                            Modifier.nestedScroll(
                                connection =
                                    TopBarNestedScrollConnection(
                                        maxOffset = spacerHeight,
                                        flingAnimationSpec = rememberSplineBasedDecay(),
                                        offset = { headerOffset },
                                        onOffsetUpdate = { headerOffset = it },
                                    )
                            )
                    )
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(with(LocalDensity.current) { headerOffset.toDp() }))
                    Header(onMenuOpen = onMenuOpen, modifier = Modifier.padding(horizontal = 16.dp))
                    SelectionGroupRow(
                        modifier =
                            Modifier.horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                    ) {
                        Filter.entries.forEach { filter ->
                            // "Action needed" stands out in the error colour while anything waits.
                            val highlight = filter == Filter.NeedsYou && needsYouSites.isNotEmpty()
                            SelectionGroupItem(
                                colors =
                                    if (highlight)
                                        SelectionGroupDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.error,
                                            activeContentColor = MaterialTheme.colorScheme.onError,
                                            inactiveContainerColor =
                                                MaterialTheme.colorScheme.errorContainer,
                                            inactiveContentColor =
                                                MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    else
                                        SelectionGroupDefaults.colors(
                                            activeContainerColor =
                                                LocalFixedColorRoles.current.tertiaryFixed,
                                            activeContentColor =
                                                LocalFixedColorRoles.current.onTertiaryFixed,
                                        ),
                                selected = activeFilter == filter,
                                onClick = {
                                    if (activeFilter == filter) {
                                        scope.launch { lazyListState.animateScrollToItem(0) }
                                        scope.launch {
                                            val initialValue = headerOffset
                                            AnimationState(initialValue = initialValue).animateTo(
                                                spacerHeight
                                            ) {
                                                headerOffset = value
                                            }
                                        }
                                    } else {
                                        activeFilter = filter
                                    }
                                },
                            ) {
                                Text(
                                    if (filter == Filter.NeedsYou && needsYouSites.isNotEmpty())
                                        "${filter.label()} (${needsYouSites.sumOf { it.second.size }})"
                                    else filter.label()
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (headerOffset <= 0.1f && spacerHeight > 0f) {
                        HorizontalDivider(thickness = Dp.Hairline)
                    }
                }

                LazyVerticalGrid(
                    modifier = Modifier,
                    state = lazyListState,
                    columns = GridCells.Adaptive(240.dp),
                    contentPadding =
                        windowInsetsPadding +
                            PaddingValues(start = 20.dp, end = 20.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (filteredMap.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val videoCount =
                                filteredMap.count {
                                    !it.value.viewState.videoFormats.isNullOrEmpty()
                                }
                            SubHeader(
                                modifier = Modifier,
                                videoCount = videoCount,
                                audioCount = filteredMap.size - videoCount,
                                isGridView = isGridView,
                                onToggleView = { isGridView = !isGridView },
                                onBulkAction = onBulkAction,
                            )
                        }
                    }
                    if (storagePaused) {
                        item(span = { GridItemSpan(maxLineSpan) }) { StorageLowBanner() }
                    }
                    if (needsYouSites.isNotEmpty() && activeFilter == Filter.All) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NeedsYouBanner(
                                videoCount = needsYouSites.sumOf { it.second.size },
                                siteCount = needsYouSites.size,
                                onView = { activeFilter = Filter.NeedsYou },
                            )
                        }
                    }
                    if (activeFilter == Filter.NeedsYou) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NeedsYouSiteList(
                                sites = needsYouSites,
                                onOpen = { site, tasks ->
                                    SiteCheckActivity.open(context, site, tasks.first().url)
                                },
                            )
                        }
                    }

                    if (isGridView) {
                        items(
                            items =
                                filteredMap.toList().sortedBy { (_, state) -> state.downloadState },
                            key = { (task, _) -> task.id },
                        ) { (task, state) ->
                            with(state.viewState) {
                                VideoCardV2(
                                    modifier = Modifier.padding(bottom = 20.dp).padding(),
                                    viewState = if (previewDisabled) copy(thumbnailUrl = null) else this,
                                    actionButton = {
                                        ActionButton(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        ) {
                                            onActionPost(task, it)
                                        }
                                    },
                                    stateIndicator = {
                                        CardStateIndicator(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        )
                                    },
                                    onButtonClick = { showActionSheet(task) },
                                )
                            }
                        }
                    } else {
                        items(
                            items =
                                filteredMap.toList().sortedBy { (_, state) -> state.downloadState },
                            key = { (task, _) -> task.id },
                            span = { GridItemSpan(maxLineSpan) },
                        ) { (task, state) ->
                            VideoListItem(
                                modifier = Modifier.padding(bottom = 16.dp),
                                viewState =
                                    if (previewDisabled) state.viewState.copy(thumbnailUrl = null)
                                    else state.viewState,
                                stateIndicator = {
                                    ListItemStateText(
                                        modifier = Modifier.padding(top = 3.dp),
                                        downloadState = state.downloadState,
                                    )
                                },
                                onButtonClick = { showActionSheet(task) },
                            )
                        }
                    }
                }
            }
        }
        if (filteredMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                DownloadQueuePlaceholder(
                    modifier =
                        Modifier.fillMaxHeight(0.4f).widthIn(max = 360.dp).align(Alignment.Center)
                )
            }
        }
    }
    if (selectedTask != null) {
        val task = selectedTask!!
        val (downloadState, _, viewState) = taskDownloadStateMap[task] ?: return
        SealModalBottomSheet(
            sheetState = sheetState,
            contentPadding = PaddingValues(),
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
            },
        ) {
            SheetContent(
                task = task,
                downloadState = downloadState,
                viewState = viewState,
                onDismissRequest = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
                },
                onActionPost = onActionPost,
            )
        }
    }
}

@Composable
fun Header(modifier: Modifier = Modifier, onMenuOpen: () -> Unit = {}) {
    val windowWidthSizeClass = LocalWindowWidthState.current
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.Expanded -> {
            HeaderExpanded(modifier = modifier)
        }
        else -> {
            HeaderCompact(modifier = modifier, onMenuOpen = onMenuOpen)
        }
    }
}

@Composable
private fun HeaderCompact(modifier: Modifier = Modifier, onMenuOpen: () -> Unit) {

    Row(modifier = modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenuOpen, modifier = Modifier) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.show_navigation_drawer),
                modifier = Modifier,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            stringResource(R.string.download_queue),
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

@Composable
private fun HeaderExpanded(modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            stringResource(R.string.download_queue),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun FABs(modifier: Modifier = Modifier, downloadCallback: () -> Unit = {}) {
    val expanded = LocalWindowWidthState.current != WindowWidthSizeClass.Compact
    Column(modifier = modifier.padding(6.dp), horizontalAlignment = Alignment.End) {
        FloatingActionButton(
            onClick = downloadCallback,
            content = {
                if (expanded) {
                    Row(
                        modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.download))
                    }
                } else {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = stringResource(R.string.download),
                    )
                }
            },
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
@Preview
private fun DownloadQueuePlaceholder(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        ConstraintLayout {
            val (image, text) = createRefs()
            val showImage =
                with(LocalDensity.current) {
                    this@BoxWithConstraints.constraints.maxHeight >= 240.dp.toPx()
                }
            if (showImage) {
                Image(
                    painter = rememberVectorPainter(image = DynamicColorImageVectors.download()),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxHeight(0.5f).widthIn(max = 240.dp).constrainAs(image) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
                )
            } else {
                Spacer(Modifier.height(72.dp).constrainAs(image) { top.linkTo(parent.top) })
            }
            Column(
                modifier = Modifier.constrainAs(text) { top.linkTo(image.bottom, margin = 36.dp) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.you_ll_find_your_downloads_here),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.download_hint),
                    modifier = Modifier.padding(top = 4.dp).padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SubHeader(
    modifier: Modifier = Modifier,
    containerColor: Color =
        MaterialTheme.colorScheme.run {
            if (LocalDarkTheme.current.isDarkTheme()) surfaceContainer else surfaceContainerLowest
        },
    videoCount: Int = 0,
    audioCount: Int = 0,
    isGridView: Boolean = true,
    onToggleView: () -> Unit,
    onBulkAction: (BulkAction) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<BulkAction?>(null) }
    val text = buildString {
        if (videoCount > 0) {
            append(pluralStringResource(R.plurals.video_count, videoCount).format(videoCount))
            if (audioCount > 0) {
                append(", ")
            }
        }
        if (audioCount > 0) {
            append(pluralStringResource(R.plurals.audio_count, audioCount).format(audioCount))
        }
    }

    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledIconButton(
            onClick = onToggleView,
            modifier = Modifier.clearAndSetSemantics {}.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
        ) {
            Icon(
                imageVector =
                    if (isGridView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        Box {
            FilledIconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                BulkAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.labelRes)) },
                        onClick = {
                            showMenu = false
                            if (action.needsConfirmation) confirmAction = action
                            else onBulkAction(action)
                        },
                    )
                }
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(stringResource(action.labelRes)) },
            text = { action.confirmMessageRes?.let { Text(stringResource(it)) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        onBulkAction(action)
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Actions in the ⋮ menu above the download list. */
enum class BulkAction(
    val labelRes: Int,
    val needsConfirmation: Boolean = false,
    val confirmMessageRes: Int? = null,
) {
    CancelAll(R.string.menu_cancel_all, true, R.string.menu_cancel_all_confirm),
    ResumeCanceled(R.string.menu_resume_canceled),
    RetryFailed(R.string.menu_retry_failed),
    ClearFinished(R.string.menu_clear_finished),
    ClearFailed(R.string.menu_clear_failed, true, R.string.menu_clear_failed_confirm),
}

@Composable
private fun StorageLowBanner() {
    val context = LocalContext.current
    val reason by StorageGuard.reason.collectAsState()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = StorageGuard.titleFor(context, reason),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = StorageGuard.messageFor(context, reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal class DownloadPageV2Test {
    private val mockDownloader =
        object : DownloaderV2 {
            private val map = mutableStateMapOf<Task, Task.State>()

            init {
                val viewState =
                    Task.ViewState(title = "Sample title", uploader = "dummy video uploader")
                val list =
                    listOf(
                        Task.State(Idle, null, viewState),
                        Task.State(Canceled(Task.RestartableAction.Download), null, viewState),
                        Task.State(Completed(null), null, viewState),
                    )
                map.run {
                    repeat(9) {
                        put(Task(url = "$it", preferences = PreferencesMock), list[it % 3])
                    }
                }
                val scope = CoroutineScope(SupervisorJob())

                scope.launch(Dispatchers.Default) {
                    while (true) {
                        delay(1000)
                        val newEntries =
                            map.toMap().map { (task, state) ->
                                val newDownloadState =
                                    when (state.downloadState) {
                                        is Canceled -> Idle
                                        is Completed -> Idle
                                        is Error -> Idle
                                        is FetchingInfo -> ReadyWithInfo
                                        Idle -> FetchingInfo(Job(), task.id)
                                        ReadyWithInfo -> Running(Job(), task.id)
                                        is Running -> {
                                            val preState: Running = state.downloadState
                                            if (preState.progress >= 1f) Completed(null)
                                            else preState.copy(progress = preState.progress + 0.1f)
                                        }
                                    }
                                task to state.copy(downloadState = newDownloadState)
                            }
                        Snapshot.withMutableSnapshot {
                            newEntries.forEach { (task, state) ->
                                delay(100)
                                map[task] = state
                            }
                        }
                    }
                }
            }

            override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
                return map
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

    @Composable
    @Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
    @Preview(name = "Tablet", device = "spec:width=600dp,height=800dp,dpi=240")
    private fun Preview() {

        val downloader: DownloaderV2 = mockDownloader
        SealTheme {
            Column() {
                DownloadPageImplV2(
                    taskDownloadStateMap = downloader.getTaskStateMap(),
                    onActionPost = { task, state -> },
                    onMenuOpen = {},
                )
            }
        }
    }
}

/** Shown on the All tab when some downloads are waiting for the user. Meant to stand out. */
@Composable
private fun NeedsYouBanner(videoCount: Int, siteCount: Int, onView: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        onClick = onView,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        pluralStringResource(R.plurals.needs_you_banner_title, videoCount, videoCount) +
                            " · " +
                            pluralStringResource(R.plurals.needs_you_sites, siteCount, siteCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.needs_you_banner_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FilledTonalButton(onClick = onView, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.needs_you_view))
            }
        }
    }
}

/** The "Needs you" tab: one row per site, with how many videos wait and an Open button. */
@Composable
private fun NeedsYouSiteList(
    sites: List<Pair<String, List<Task>>>,
    onOpen: (String, List<Task>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.needs_you_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        sites.forEach { (site, tasks) ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = site, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.needs_you_waiting,
                                    tasks.size,
                                    tasks.size,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { onOpen(site, tasks) }) {
                        Text(stringResource(R.string.needs_you_open))
                    }
                }
            }
        }
    }
}
