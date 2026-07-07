package id.andreasmbngaol.agallery.presentation.gallery

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.CheckSquare
import com.adamglin.phosphoricons.bold.Copy
import com.adamglin.phosphoricons.bold.FolderSimple
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ImageSquare
import com.adamglin.phosphoricons.bold.Square
import com.adamglin.phosphoricons.bold.Trash
import com.adamglin.phosphoricons.bold.WarningCircle
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.Trash
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnail
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import id.andreasmbngaol.agallery.domain.model.album.mediaScopeFromKey
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import id.andreasmbngaol.agallery.presentation.viewer.AlbumThumbnailPickerDialog
import id.andreasmbngaol.agallery.presentation.viewer.HoldToDeleteButton
import id.andreasmbngaol.agallery.presentation.viewer.MoveToTrashConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

private val GalleryTopAppBarHeight = 72.dp

private val PullContentMaxOffset = 80.dp

private val PullIndicatorSize = 40.dp

private const val GridThumbnailPx = 400
private fun PerformanceMode.prefetchRows(): Pair<Int, Int> = when (this) {
    PerformanceMode.LOW -> 2 to 0
    PerformanceMode.BALANCED -> 6 to 2
    PerformanceMode.HIGH -> 12 to 4
}

private val ScrollbarTouchWidth = 32.dp
private val ScrollbarThumbWidth = 8.dp
private val ScrollbarThumbDragWidth = 16.dp
private val ScrollbarThumbHeight = 52.dp
private val ScrollbarEndPadding = 4.dp
private val ScrollbarEdgeInset = 4.dp

private fun titleDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private fun formatTitleDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(titleDateFormatter())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * The Gallery tab content (photo grid).
 *
 * Hosted inside the pager
 * [id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen]; the floating bar
 * (Gallery/Albums, Settings, Sort) is drawn by that host, NOT here.
 * This screen used to wrap itself in a `GalleryTabScaffold`.
 *
 * Preferences (sortOrder, column count, edge effect) are read from
 * [GalleryViewModel], which persists them to DataStore, so they react to
 * changes in the Settings screen and PERSIST even after the app is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridScreen(
    onMediaClick: (mediaId: Long, index: Int, sortOrder: GallerySortOrder) -> Unit,
    onScrollStateChange: (Boolean) -> Unit = {},
    staticTitle: String? = null,
    onBack: (() -> Unit)? = null,
    selectionEnabled: Boolean = false,
    albumKey: String? = null,
    viewModel: GalleryViewModel = koinViewModel(),
) {
    val sortOrder by viewModel.sortOrder.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val chosenMode by viewModel.edgeEffectMode.collectAsState()
    val effectiveMode = rememberEffectiveEdgeEffectMode(chosenMode)
    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    val topBarBackdrop = rememberLayerBackdrop()

    val gridState = rememberLazyGridState()
    val items = viewModel.media.collectAsLazyPagingItems()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { onScrollStateChange(it) }
    }

    val defaultTitle = stringResource(R.string.gallery_title)
    val topBarTitle by remember(items, staticTitle, defaultTitle) {
        derivedStateOf {
            if (staticTitle != null) {
                staticTitle
            } else {
                val isAtTop = gridState.firstVisibleItemIndex == 0 &&
                        gridState.firstVisibleItemScrollOffset == 0
                if (isAtTop || items.itemCount == 0) {
                    defaultTitle
                } else {
                    items.peek(gridState.firstVisibleItemIndex)
                        ?.dateAddedEpochSeconds
                        ?.takeIf { it > 0L }
                        ?.let(::formatTitleDate)
                        ?: defaultTitle
                }
            }
        }
    }

    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val gridContentPadding = PaddingValues(
        top = safeDrawingPadding.calculateTopPadding() + GalleryTopAppBarHeight,
        bottom = safeDrawingPadding.calculateBottomPadding() +
            (if (onBack != null) 0.dp else FloatingTabBarHeight),
        start = safeDrawingPadding.calculateStartPadding(layoutDirection),
        end = safeDrawingPadding.calculateEndPadding(layoutDirection),
    )

    val previewItem by viewModel.previewItem.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    val albums by viewModel.albums.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Long, MediaItem>() }
    val isSpecialAlbum = remember(albumKey) {
        when (albumKey?.let { mediaScopeFromKey(it) }) {
            MediaScope.AllMedia, MediaScope.AllVideos, MediaScope.Favorites -> true
            else -> false
        }
    }
    fun toggleSelect(item: MediaItem) {
        if (selected.containsKey(item.id)) selected.remove(item.id)
        else selected[item.id] = item
    }
    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }
    BackHandler(enabled = selectionMode) { exitSelection() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            items.refresh()
            exitSelection()
        }
        viewModel.dismissPreview()
    }

    LaunchedEffect(Unit) {
        viewModel.deleteRequests.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(Unit) {
        viewModel.mediaDeleted.collect {
            items.refresh()
            viewModel.dismissPreview()
            exitSelection()
        }
    }
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onWriteConsentGranted()
        else viewModel.onWriteConsentDenied()
    }
    LaunchedEffect(Unit) {
        viewModel.writeRequests.collect { sender ->
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(Unit) {
        viewModel.selectionActionDone.collect {
            items.refresh()
            exitSelection()
        }
    }

    val backgroundBlur by animateDpAsState(
        targetValue = if (previewItem != null) 24.dp else 0.dp,
        label = "preview-bg-blur",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().blur(backgroundBlur)) {
    SystemBarScrim(
        mode = effectiveMode,
        topExtraHeight = GalleryTopAppBarHeight,
        topOverlay = {
            TopAppBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    AnimatedContent(
                        targetState = topBarTitle,
                        transitionSpec = {
                            (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "gallery-title",
                    ) { title ->
                        Text(
                            text = title,
                            style = LocalTextStyle.current.copy(
                                fontSize = 26.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f,
                                ),
                            ),
                            modifier = if (onBack != null) {
                                Modifier.padding(start = 12.dp)
                            } else {
                                Modifier
                            },
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            StyledCircleBackButton(
                                style = componentStyle,
                                backdrop = topBarBackdrop,
                                onClick = onBack,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { sourceModifier ->
        val gridBackdropModifier = if (componentStyle.drawsBackdrop() && (previewItem != null || onBack != null)) {
            Modifier.fillMaxSize().layerBackdrop(topBarBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = gridBackdropModifier) {
            GalleryPagingContent(
                items = items,
                gridState = gridState,
                contentPadding = gridContentPadding,
                modifier = sourceModifier,
                gridColumns = gridColumns,
                performanceMode = performanceMode,
                onMediaClick = { id, index -> onMediaClick(id, index, sortOrder) },
                onLongPress = { item -> viewModel.showPreview(item) },
                selectionMode = selectionMode,
                isSelected = { selected.containsKey(it) },
                onToggleSelect = { toggleSelect(it) },
            )
        }
    }
        }

        var pendingDeleteUri by remember { mutableStateOf<String?>(null) }

        previewItem?.let { preview ->
            PhotoPreviewOverlay(
                item = preview,
                backdrop = topBarBackdrop,
                style = componentStyle,
                isFavorite = preview.id in favoriteIds,
                onDismiss = { viewModel.dismissPreview() },
                onFavoriteClick = {
                    viewModel.onToggleFavorite(preview.id, preview.id !in favoriteIds)
                },
                onTrashClick = { viewModel.moveToTrash(preview) },
                onDeleteClick = { pendingDeleteUri = preview.uri },
            )
        }

        pendingDeleteUri?.let { uri ->
            ConfirmDeleteDialog(
                count = 1,
                onConfirm = {
                    viewModel.deletePhoto(uri)
                    pendingDeleteUri = null
                },
                onDismiss = { pendingDeleteUri = null },
            )
        }

        if (selectionEnabled && items.itemCount > 0) {
            var batchAlbumMode by remember { mutableStateOf<BatchAlbumMode?>(null) }
            var showBatchTrashConfirm by remember { mutableStateOf(false) }
            var showBatchDeleteConfirm by remember { mutableStateOf(false) }
            val selCount = selected.size
            val loadedItems = items.itemSnapshotList.items
            val allSelected = loadedItems.isNotEmpty() &&
                loadedItems.all { selected.containsKey(it.id) }
            val tint = MaterialTheme.colorScheme.onSurface

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .selectionGlass(componentStyle, topBarBackdrop)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!selectionMode) {
                    IslandAction(
                        icon = PhosphorIcons.Bold.CheckSquare,
                        label = stringResource(R.string.action_select),
                        tint = tint,
                        onClick = { selectionMode = true },
                    )
                } else {
                    IslandAction(
                        icon = if (allSelected) PhosphorIcons.Bold.Square
                        else PhosphorIcons.Bold.CheckSquare,
                        label = if (allSelected) stringResource(R.string.action_select_none) else stringResource(R.string.action_select_all),
                        tint = tint,
                        onClick = {
                            if (allSelected) {
                                selected.clear()
                            } else {
                                selected.clear()
                                loadedItems.forEach { selected[it.id] = it }
                            }
                        },
                    )
                    if (selCount > 0) {
                        if (!isSpecialAlbum) {
                            IslandAction(
                                icon = PhosphorIcons.Bold.Copy,
                                label = stringResource(R.string.action_copy_to_album),
                                tint = tint,
                                onClick = {
                                    viewModel.loadAlbums()
                                    batchAlbumMode = BatchAlbumMode.COPY
                                },
                            )
                            IslandAction(
                                icon = PhosphorIcons.Bold.FolderSimple,
                                label = stringResource(R.string.action_move_to_album),
                                tint = tint,
                                onClick = {
                                    viewModel.loadAlbums()
                                    batchAlbumMode = BatchAlbumMode.MOVE
                                },
                            )
                        }
                        HoldToDeleteButton(
                            onTap = { showBatchTrashConfirm = true },
                            onHoldComplete = { showBatchDeleteConfirm = true },
                            tint = tint,
                        )
                    }
                    IslandAction(
                        icon = PhosphorIcons.Bold.X,
                        label = stringResource(R.string.action_cancel),
                        tint = tint,
                        onClick = { exitSelection() },
                    )
                }
            }

            batchAlbumMode?.let { mode ->
                AlbumThumbnailPickerDialog(
                    title = if (mode == BatchAlbumMode.COPY) stringResource(R.string.action_copy_to_album) else stringResource(R.string.action_move_to_album),
                    albums = albums,
                    onPick = { name ->
                        val picked = selected.values.toList()
                        if (mode == BatchAlbumMode.COPY) {
                            viewModel.copyManyToAlbum(picked, name)
                        } else {
                            viewModel.moveManyToAlbum(picked, name)
                        }
                        batchAlbumMode = null
                    },
                    onDismiss = { batchAlbumMode = null },
                )
            }

            if (showBatchTrashConfirm) {
                MoveToTrashConfirmDialog(
                    onConfirm = {
                        viewModel.moveManyToTrash(selected.values.toList())
                        showBatchTrashConfirm = false
                    },
                    onDismiss = { showBatchTrashConfirm = false },
                )
            }

            if (showBatchDeleteConfirm) {
                ConfirmDeleteDialog(
                    count = selCount,
                    onConfirm = {
                        viewModel.deleteMany(selected.values.map { it.uri })
                        showBatchDeleteConfirm = false
                    },
                    onDismiss = { showBatchDeleteConfirm = false },
                )
            }
        }

    }
}

/** Album picker mode for batch actions in the selection island. */
private enum class BatchAlbumMode { COPY, MOVE }

/**
 * A liquid-glass capsule modifier (identical pattern to the Trash/PhotoViewer island).
 * GLASS = blur + lens; FROSTED = vibrancy + veil; SOLID/API<33 = fallback fill.
 */
@Composable
private fun Modifier.selectionGlass(style: ComponentStyle, backdrop: Backdrop): Modifier {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) 0.4f else 0.3f,
    )
    val fallbackTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) 0.55f else 0.95f,
    )
    return if (style.drawsBackdrop()) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                if (style.usesBlur()) blur(4.dp.toPx())
                if (style.usesLens()) lens(12.dp.toPx(), 16.dp.toPx())
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        clip(RoundedCornerShape(percent = 50)).background(fallbackTint)
    }
}

/** A single action in the island: just a Phosphor icon (label for contentDescription). */
@Composable
private fun IslandAction(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Check circle: empty (white outline) or filled primary + a checkmark. */
@Composable
private fun SelectionCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) primary else Color.Black.copy(alpha = 0.35f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = PhosphorIcons.Bold.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun GalleryPagingContent(
    items: LazyPagingItems<MediaItem>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    gridColumns: Int,
    performanceMode: PerformanceMode,
    onMediaClick: (Long, Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
    selectionMode: Boolean = false,
    isSelected: (Long) -> Boolean = { false },
    onToggleSelect: (MediaItem) -> Unit = {},
) {
    when (val refresh = items.loadState.refresh) {
        is LoadState.Loading if items.itemCount == 0 -> {
            LoadingState(contentPadding = contentPadding)
        }

        is LoadState.Error if items.itemCount == 0 -> {
            GalleryPullToRefresh(
                items = items,
                contentPadding = contentPadding,
                modifier = modifier,
            ) { contentOffset ->
                ErrorState(
                    contentPadding = contentPadding,
                    message = refresh.error.localizedMessage
                        ?: stringResource(R.string.gallery_load_error),
                    onRetry = { items.retry() },
                    modifier = contentOffset,
                )
            }
        }

        is LoadState.NotLoading if items.itemCount == 0 -> {
            GalleryPullToRefresh(
                items = items,
                contentPadding = contentPadding,
                modifier = modifier,
            ) { contentOffset ->
                EmptyState(
                    contentPadding = contentPadding,
                    modifier = contentOffset,
                )
            }
        }

        else -> GalleryGrid(
            items = items,
            gridState = gridState,
            contentPadding = contentPadding,
            modifier = modifier,
            gridColumns = gridColumns,
            performanceMode = performanceMode,
            onMediaClick = onMediaClick,
            onLongPress = onLongPress,
            selectionMode = selectionMode,
            isSelected = isSelected,
            onToggleSelect = onToggleSelect,
        )
    }
}

/**
 * An iOS-style pull-to-refresh wrapper SHARED by the grid, empty
 * state, and error state — so the user can still pull-to-refresh EVEN WHEN the
 * gallery is still empty (e.g. right after permission is granted, before content
 * has loaded).
 *
 * [content] receives a `translationY` Modifier (contentOffset) that
 * MUST be attached to its scrollable element, so the content is dragged down
 * following the finger and the indicator appears in the gap above it.
 *
 * Important note: the element inside [content] MUST be scrollable
 * (LazyVerticalGrid for the grid; LazyColumn for empty/error) so the
 * nested-scroll pull can be detected even when there is little/no content. A plain
 * Column will not trigger pull-to-refresh.
 *
 * Different from the Material3 default (floating indicator, static content): here
 * the CONTENT is dragged down via `graphicsLayer`
 * `translationY = distanceFraction * maxOffset`, and the indicator FLOATS in the
 * MIDDLE of the opened gap. The sequence:
 *   1. Finger pulls → content shifts down, the indicator appears in the gap above it.
 *   2. Release past the threshold → `items.refresh()` runs, the spinner spins.
 *   3. Refresh done (`isRefreshing` false) → state returns to hidden, the content
 *      rises again & the indicator fades out. All automatic from PullToRefreshBox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryPullToRefresh(
    items: LazyPagingItems<MediaItem>,
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable (contentOffset: Modifier) -> Unit,
) {
    val isRefreshing = items.loadState.refresh is LoadState.Loading

    val pullState = rememberPullToRefreshState()

    val density = LocalDensity.current
    val maxOffsetPx = with(density) { PullContentMaxOffset.toPx() }
    val topInsetPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val indicatorHalfPx = with(density) { (PullIndicatorSize / 2).toPx() }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { items.refresh() },
        state = pullState,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        indicator = {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(PullIndicatorSize)
                    .graphicsLayer {
                        val fraction = pullState.distanceFraction.coerceAtLeast(0f)
                        translationY =
                            topInsetPx + (fraction * maxOffsetPx) / 2f - indicatorHalfPx
                        alpha = fraction.coerceIn(0f, 1f)
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { pullState.distanceFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }
        },
    ) {
        val contentOffset = Modifier.graphicsLayer {
            translationY = pullState.distanceFraction.coerceAtLeast(0f) * maxOffsetPx
        }
        content(contentOffset)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
    items: LazyPagingItems<MediaItem>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    gridColumns: Int,
    performanceMode: PerformanceMode,
    onMediaClick: (Long, Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
    selectionMode: Boolean = false,
    isSelected: (Long) -> Boolean = { false },
    onToggleSelect: (MediaItem) -> Unit = {},
) {
    GalleryPullToRefresh(
        items = items,
        contentPadding = contentPadding,
        modifier = modifier,
    ) { contentOffset ->
        val context = LocalContext.current

        val imageLoader = SingletonImageLoader.get(context)
        val (rowsAhead, rowsBack) = performanceMode.prefetchRows()
        LaunchedEffect(gridState, items, gridColumns, rowsAhead, rowsBack) {
            snapshotFlow { gridState.isScrollInProgress }
                .distinctUntilChanged()
                .collectLatest { scrolling ->
                    if (scrolling) return@collectLatest
                    delay(120.milliseconds)
                    val info = gridState.layoutInfo.visibleItemsInfo
                    val firstVisible = info.firstOrNull()?.index ?: return@collectLatest
                    val lastVisible = info.lastOrNull()?.index ?: return@collectLatest
                    val lastIndex = items.itemCount - 1
                    if (lastIndex < 0) return@collectLatest
                    withContext(Dispatchers.Default) {
                        val start = (firstVisible - gridColumns * rowsBack)
                            .coerceAtLeast(0)
                        val end = (lastVisible + gridColumns * rowsAhead)
                            .coerceAtMost(lastIndex)
                        for (i in start..end) {
                            if (i in firstVisible..lastVisible) continue
                            val media = items.peek(i) ?: continue
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(
                                        MediaStoreThumbnail(
                                            uri = media.uri,
                                            isVideo = media.type == MediaType.VIDEO,
                                        ),
                                    )
                                    .size(GridThumbnailPx, GridThumbnailPx)
                                    .build(),
                            )
                        }
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize().then(contentOffset)) {
        LazyVerticalGrid(
            state = gridState,
            flingBehavior = rememberSensitiveFlingBehavior(),
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType { it.type },
            ) { index ->
                val item = items[index]
                if (item == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    return@items
                }
                val isSel = isSelected(item.id)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedPhotoElement(key = "photo-${item.id}")
                        .then(
                            if (isSel) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) onToggleSelect(item)
                                else onMediaClick(item.id, index)
                            },
                            onLongClick = {
                                if (selectionMode) onToggleSelect(item)
                                else onLongPress(item)
                            },
                        ),
                ) {
                    AsyncImage(
                        model = remember(item.uri, item.type) {
                            ImageRequest.Builder(context)
                                .data(
                                    MediaStoreThumbnail(
                                        uri = item.uri,
                                        isVideo = item.type == MediaType.VIDEO,
                                    ),
                                )
                                .size(GridThumbnailPx, GridThumbnailPx)
                                .crossfade(false)
                                .build()
                        },
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.type == MediaType.VIDEO) {
                        VideoBadge(
                            durationMs = item.durationMs,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                        )
                    }
                    if (selectionMode) {
                        SelectionCheck(
                            selected = isSel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }

            GridFastScrollbar(
                gridState = gridState,
                columns = gridColumns,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                        end = ScrollbarEdgeInset,
                    ),
            )
        }
    }
}

/**
 * A small badge in the thumbnail corner for video items: a play icon + duration
 * (m:ss / h:mm:ss). A semi-transparent black background so it stays readable over
 * any frame.
 */
@Composable
private fun VideoBadge(
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = PhosphorIcons.Fill.Play,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        if (durationMs > 0L) {
            Text(
                text = formatVideoDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Format video duration: m:ss, or h:mm:ss when >= 1 hour. */
private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LoadingState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Empty state. Wrapped in a [LazyColumn] (not a plain Column) + a
 * `fillParentMaxSize` item so: (a) the message stays centered in the viewport, and
 * (b) the container is scrollable so nested-scroll pull-to-refresh can be
 * triggered even when there is little content.
 */
@Composable
private fun EmptyState(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = PhosphorIcons.Bold.ImageSquare,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.gallery_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.gallery_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Error state — scrollable (LazyColumn) so pull-to-refresh still works. */
@Composable
private fun ErrorState(
    contentPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = PhosphorIcons.Bold.WarningCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.gallery_error_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_try_again))
                }
            }
        }
    }
}

/**
 * Higher-sensitivity fling: the initial velocity is multiplied so a scroll
 * throw feels more responsive & glides farther (reducing the "stutter"
 * feel on a fast flick). Delegates to Compose's default fling.
 */
@Composable
private fun rememberSensitiveFlingBehavior(
    velocityMultiplier: Float = 1.6f,
): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    return remember(base, velocityMultiplier) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                return with(base) { performFling(initialVelocity * velocityMultiplier) }
            }
        }
    }
}

/**
 * A preview overlay when a photo is held (long-press): the photo grows to its
 * ORIGINAL aspect ratio (bounded by maxWidth/maxHeight so it stays on screen),
 * the gallery background is blurred by the caller, and a context menu
 * (liquid glass) appears for that single photo.
 *
 * Tapping the dark area / back button = close. Tapping the content area does not close.
 */
@Composable
private fun PhotoPreviewOverlay(
    item: MediaItem,
    backdrop: Backdrop,
    style: ComponentStyle,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTrashClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val maxImageWidth = maxWidth * 0.9f
            val maxImageHeight = maxHeight * 0.62f
            val enterScale = remember { Animatable(0.7f) }
            LaunchedEffect(Unit) {
                enterScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = enterScale.value
                        scaleY = enterScale.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* no-op */ },
            ) {
                AsyncImage(
                    model = remember(item.uri, item.type) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(false)
                            .apply {
                                if (item.type == MediaType.VIDEO) {
                                    videoFramePercent(0.5)
                                }
                            }
                            .build()
                    },
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .sizeIn(maxWidth = maxImageWidth, maxHeight = maxImageHeight)
                        .clip(RoundedCornerShape(16.dp)),
                )
                PhotoContextMenu(
                    backdrop = backdrop,
                    style = style,
                    isFavorite = isFavorite,
                    onFavoriteClick = onFavoriteClick,
                    onTrashClick = onTrashClick,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }
}

/**
 * A round 40dp "Back" button whose style follows the active [ComponentStyle]:
 *  - SOLID   : a semi-transparent black circle (drawsBackdrop=false).
 *  - FROSTED : a thick white haze (drawBackdrop without blur/lens).
 *  - GLASS   : Kyant blur + lens for a liquid-glass effect.
 *
 * Used in the Gallery TopBar ONLY when onBack is non-null (album-detail mode).
 */
@Composable
private fun StyledCircleBackButton(
    style: ComponentStyle,
    backdrop: Backdrop,
    onClick: () -> Unit,
) {
    val size = 40.dp
    val onBg = MaterialTheme.colorScheme.onBackground
    val circleModifier = if (style.drawsBackdrop()) {
        Modifier
            .size(size)
            .clip(CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    if (style.usesBlur()) {
                        blur(4.dp.toPx())
                    }
                    if (style.usesLens()) {
                        lens(12.dp.toPx(), 16.dp.toPx())
                    }
                },
                onDrawSurface = {
                    drawRect(
                        onBg.copy(
                            alpha = if (style == ComponentStyle.FROSTED) 0.18f else 0.14f,
                        ),
                    )
                },
            )
    } else {
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(onBg.copy(alpha = 0.16f))
    }

    Box(
        modifier = circleModifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Bold.ArrowLeft,
            contentDescription = stringResource(R.string.action_back),
            tint = onBg,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A single-photo context menu with a LIQUID GLASS background (Kyant drawBackdrop;
 * frosted fallback for API < 33). Contains 3 side-by-side actions:
 *  - Favorite : toggle favorite (local Room). A FILLED heart icon when active.
 *  - Trash    : move to Trash (soft-delete, recoverable for 30 days).
 *  - Delete   : PERMANENTLY delete from the device (scoped-storage system dialog).
 */
@Composable
private fun PhotoContextMenu(
    backdrop: Backdrop,
    style: ComponentStyle,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onTrashClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val menuShape = RoundedCornerShape(percent = 50)
    val deleteColor = Color(0xFFFF453A)
    val favoriteColor = Color(0xFFFF375F)
    val neutralColor = Color.White

    val glassModifier = if (style.drawsBackdrop()) {
        Modifier
            .clip(menuShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur((if (style.usesLens()) 8.dp else 24.dp).toPx())
                    if (style.usesLens()) {
                        lens(12.dp.toPx(), 16.dp.toPx())
                    }
                },
                onDrawSurface = {
                    drawRect(
                        Color.White.copy(alpha = if (style == ComponentStyle.FROSTED) 0.3f else 0.18f),
                    )
                },
            )
    } else {
        Modifier
            .clip(menuShape)
            .background(Color.Black.copy(alpha = if (style == ComponentStyle.FROSTED) 0.5f else 0.8f))
    }

    Row(
        modifier = glassModifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MenuAction(
            icon = if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
            label = if (isFavorite) stringResource(R.string.action_favorited) else stringResource(R.string.action_favorite),
            tint = if (isFavorite) favoriteColor else neutralColor,
            onClick = onFavoriteClick,
        )
        MenuAction(
            icon = PhosphorIcons.Bold.Trash,
            label = stringResource(R.string.trash_title),
            tint = neutralColor,
            onClick = onTrashClick,
        )
        MenuAction(
            icon = PhosphorIcons.Fill.Trash,
            label = stringResource(R.string.action_delete),
            tint = deleteColor,
            onClick = onDeleteClick,
        )
    }
}

/**
 * A single action button in [PhotoContextMenu]: icon on top, small label below,
 * with its own (rounded) tap area so the 3 actions don't overlap each other.
 */
@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * A fast-scroll scrollbar for the gallery grid: a pill grip at the right edge that
 * appears while scrolling / dragging, then fades out. Can be DRAGGED to jump
 * quickly to any position (e.g. straight to the very bottom).
 *
 * The thumb position is computed from the proportion of the first visible row to the
 * total rows (`firstVisibleItemIndex / columns`). `translationY` + `alpha` are read
 * inside `graphicsLayer` (draw phase) so they update every frame WITHOUT recompose.
 */
@Composable
private fun GridFastScrollbar(
    gridState: LazyGridState,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val columnsSafe = columns.coerceAtLeast(1)
    var dragging by remember { mutableStateOf(false) }

    val scrollable by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.isNotEmpty() &&
                info.totalItemsCount > info.visibleItemsInfo.size
        }
    }

    val progress by remember(columnsSafe) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val visibleRows = ceil(info.visibleItemsInfo.size / columnsSafe.toFloat())
            val totalRows = ceil(info.totalItemsCount / columnsSafe.toFloat())
            val maxRow = (totalRows - visibleRows).coerceAtLeast(1f)
            (gridState.firstVisibleItemIndex / columnsSafe.toFloat() / maxRow)
                .coerceIn(0f, 1f)
        }
    }

    val active = (gridState.isScrollInProgress || dragging) && scrollable
    val thumbAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = if (active) 150 else 600),
        label = "scrollbar-alpha",
    )

    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollbarTouchWidth)
            .pointerInput(columnsSafe) {
                val trackPx = size.height.toFloat()
                val thumbPx = ScrollbarThumbHeight.toPx()
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    val denom = (trackPx - thumbPx).coerceAtLeast(1f)
                    val fraction =
                        ((change.position.y - thumbPx / 2f) / denom).coerceIn(0f, 1f)
                    val info = gridState.layoutInfo
                    val totalRows = ceil(info.totalItemsCount / columnsSafe.toFloat())
                    val targetRow = (fraction * (totalRows - 1f)).toInt().coerceAtLeast(0)
                    val targetIndex = (targetRow * columnsSafe)
                        .coerceIn(0, (info.totalItemsCount - 1).coerceAtLeast(0))
                    scope.launch { gridState.scrollToItem(targetIndex) }
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { ScrollbarThumbHeight.toPx() }
        val thumbWidth by animateDpAsState(
            targetValue = if (dragging) ScrollbarThumbDragWidth else ScrollbarThumbWidth,
            animationSpec = tween(durationMillis = 150),
            label = "scrollbar-thumb-width",
        )
        Box(
            modifier = Modifier
                .padding(end = ScrollbarEndPadding)
                .width(thumbWidth)
                .height(ScrollbarThumbHeight)
                .graphicsLayer {
                    translationY = progress * (trackHeightPx - thumbHeightPx)
                    alpha = thumbAlpha
                }
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
