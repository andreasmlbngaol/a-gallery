package id.andreasmbngaol.agallery.presentation.trash

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Info
import com.adamglin.phosphoricons.bold.Trash
import com.adamglin.phosphoricons.fill.Play
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
import id.andreasmbngaol.agallery.core.permission.AllFilesAccess
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.model.trash.TrashItem
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import id.andreasmbngaol.agallery.presentation.viewer.VideoPlayerContent
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val GridThumbnailPx = 400

/**
 * App-level Trash screen (30-day soft-delete).
 *
 * ## Styling consistency (following GalleryGridScreen / PhotoViewer)
 * - **Edge effect** via [SystemBarScrim] per setting (Off/Darken/Blurry).
 * - **Top bar**: a glass Back button ([GlassIconButton]) + title, following
 *   `componentStyle` (Solid/Frosted/Glass).
 * - **Bottom action island**: a liquid-glass capsule (same pattern as the
 *   PhotoViewer island) with Icon buttons + a small label below each.
 * - **Grid**: 3 columns, thumbnails cropped **1:1** (`ContentScale.Crop`) +
 *   a duration badge for videos + a days-left auto-delete badge.
 *
 * ## Interaction
 * - **Tap** an item (normal mode) -> open the in-screen viewer (pager) with
 *   LIMITED actions: only **Restore** & **Permanently delete**.
 * - **Long-press** an item -> enter selection mode (selecting that item directly).
 * - **Action island** in selection mode: Select all / Restore / Delete / Cancel.
 *
 * ## 30-day auto-purge
 * The main auto-purge now runs in the background via [id.andreasmbngaol.agallery.data.work.TrashPurgeWorker]
 * (daily) when the app has All-files access -> permanent delete without a dialog.
 * As a fallback, [TrashViewModel.autoPurgeExpired] also collects expired items
 * when the screen opens. Without All-files access, deletion still goes through a
 * SAF delete-many (one system confirmation dialog).
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
) {
    val viewModel: TrashViewModel = koinViewModel()
    val items by viewModel.items.collectAsState()
    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    val edgeModeChosen by viewModel.edgeEffectMode.collectAsState()
    val effectiveMode = rememberEffectiveEdgeEffectMode(edgeModeChosen)
    val backdrop = rememberLayerBackdrop()

    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    var pendingConfirm by remember { mutableStateOf<List<TrashItem>?>(null) }

    LaunchedEffect(items) {
        val present = items.map { it.id }.toHashSet()
        selectedIds.retainAll(present)
        if (items.isEmpty()) {
            selectionMode = false
            selectedIds.clear()
            viewerIndex = null
        } else {
            viewerIndex?.let { idx -> if (idx >= items.size) viewerIndex = items.lastIndex }
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val ids = pendingDeleteIds
        pendingDeleteIds = emptyList()
        if (result.resultCode == Activity.RESULT_OK && ids.isNotEmpty()) {
            viewModel.confirmPermanentDeleteMany(ids)
            selectedIds.clear()
            selectionMode = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteRequests.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(Unit) {
        val expired = viewModel.autoPurgeExpired()
        if (expired.isNotEmpty()) pendingDeleteIds = expired
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggle(id: Long) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    SystemBarScrim(
        mode = effectiveMode,
        topOverlay = {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = { if (selectionMode) exitSelection() else onBack() },
                    contentDescription = stringResource(R.string.action_back),
                    style = componentStyle,
                    backdrop = backdrop,
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (selectionMode) stringResource(R.string.selected_count, selectedIds.size) else stringResource(R.string.trash_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (items.isNotEmpty()) {
                TrashActionIsland(
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    allSelected = selectedIds.size == items.size,
                    style = componentStyle,
                    backdrop = backdrop,
                    onEnterSelection = { selectionMode = true },
                    onToggleSelectAll = {
                        if (selectedIds.size == items.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(items.map { it.id })
                        }
                    },
                    onRestore = {
                        viewModel.restoreMany(selectedIds.toList())
                        exitSelection()
                    },
                    onDelete = {
                        val toDelete = items.filter { it.id in selectedIds }
                        if (AllFilesAccess.isGranted()) {
                            pendingConfirm = toDelete
                        } else {
                            pendingDeleteIds = toDelete.map { it.id }
                            viewModel.requestPermanentDeleteMany(toDelete)
                        }
                    },
                    onCancel = { exitSelection() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
                )
            }
        },
    ) { sourceModifier ->
        val gridBackdropModifier = if (componentStyle.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = gridBackdropModifier.then(sourceModifier)) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.trash_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        end = 2.dp,
                        top = 96.dp,
                        bottom = 128.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = items,
                        key = { it.id },
                    ) { item ->
                        val index = items.indexOf(item)
                        TrashGridItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selectedIds,
                            onClick = {
                                if (selectionMode) toggle(item.id) else viewerIndex = index
                            },
                            onLongPress = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    if (item.id !in selectedIds) selectedIds.add(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    val openIndex = viewerIndex
    if (openIndex != null && openIndex in items.indices) {
        TrashViewer(
            items = items,
            initialIndex = openIndex,
            style = componentStyle,
            loadDetails = viewModel::loadDetails,
            onClose = { viewerIndex = null },
            onRestore = { item ->
                viewModel.restore(item.id)
                viewerIndex = null
            },
            onDelete = { item ->
                if (AllFilesAccess.isGranted()) {
                    pendingConfirm = listOf(item)
                } else {
                    pendingDeleteIds = listOf(item.id)
                    viewModel.requestPermanentDelete(item)
                }
            },
        )
    }

    val confirmItems = pendingConfirm
    if (confirmItems != null) {
        ConfirmDeleteDialog(
            count = confirmItems.size,
            onConfirm = {
                pendingConfirm = null
                pendingDeleteIds = confirmItems.map { it.id }
                viewModel.requestPermanentDeleteMany(confirmItems)
                exitSelection()
                viewerIndex = null
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

/* --------------------------------------------------------------------------
 * Grid item
 * ------------------------------------------------------------------------ */

@Composable
private fun TrashGridItem(
    item: TrashItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (selected) Modifier.border(3.dp, primary, RoundedCornerShape(4.dp))
                else Modifier,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(MediaStoreThumbnail(uri = item.uri, isVideo = item.isVideo))
                .size(GridThumbnailPx, GridThumbnailPx)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.isVideo) {
            VideoBadge(
                durationMs = item.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }

        DaysLeftBadge(
            trashedAt = item.trashedAt,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )

        if (selectionMode) {
            SelectionCheck(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }
    }
}

/** Video duration badge (Play + m:ss) -- mirrors VideoBadge in GalleryGridScreen. */
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
            modifier = Modifier.size(12.dp),
        )
        if (durationMs > 0) {
            Text(
                text = formatVideoDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Badge "Nd" (sisa hari sebelum auto-delete). */
@Composable
private fun DaysLeftBadge(
    trashedAt: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.trash_days_left, daysLeftCount(trashedAt)),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Check circle: empty (white outline) or filled primary + checkmark. */
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
        if (selected) CheckGlyph(color = Color.White, modifier = Modifier.size(14.dp))
    }
}

/* --------------------------------------------------------------------------
 * Action island (liquid glass)
 * ------------------------------------------------------------------------ */

/**
 * Liquid-glass capsule modifier (identical pattern to the PhotoViewer island).
 * GLASS = blur + lens; FROSTED = vibrancy + veil; SOLID/API<33 = fallback fill.
 */
@Composable
private fun Modifier.trashGlass(style: ComponentStyle, backdrop: Backdrop): Modifier {
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

@Composable
private fun TrashActionIsland(
    selectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    style: ComponentStyle,
    backdrop: Backdrop,
    onEnterSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .trashGlass(style, backdrop)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!selectionMode) {
            IslandAction(
                label = stringResource(R.string.action_select),
                tint = tint,
                onClick = onEnterSelection,
            ) { SelectGlyph(color = tint, modifier = Modifier.size(24.dp)) }
        } else {
            IslandAction(
                label = if (allSelected) stringResource(R.string.action_select_none) else stringResource(R.string.action_select_all),
                tint = tint,
                onClick = onToggleSelectAll,
            ) {
                if (allSelected) {
                    SquareGlyph(color = tint, modifier = Modifier.size(24.dp))
                } else {
                    SelectGlyph(color = tint, modifier = Modifier.size(24.dp))
                }
            }
            if (selectedCount > 0) {
                IslandAction(
                    icon = PhosphorIcons.Bold.ArrowClockwise,
                    label = stringResource(R.string.action_restore),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onRestore,
                )
                IslandAction(
                    icon = PhosphorIcons.Bold.Trash,
                    label = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
            IslandAction(
                label = stringResource(R.string.action_cancel),
                tint = tint,
                onClick = onCancel,
            ) { CloseGlyph(color = tint, modifier = Modifier.size(24.dp)) }
        }
    }
}

/**
 * A single action in the island: ICON ONLY (no text) to stay uniform & clean.
 * [label] is used as contentDescription for accessibility. Can use [icon]
 * (ImageVector) or a custom [glyph] slot (check/cross/checks via Canvas).
 */
@Composable
private fun IslandAction(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    glyph: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            glyph != null -> glyph()
        }
    }
}

/* --------------------------------------------------------------------------
 * Viewer in-screen (Restore / Permanently delete)
 * ------------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashViewer(
    items: List<TrashItem>,
    initialIndex: Int,
    style: ComponentStyle,
    loadDetails: suspend (String) -> MediaDetails?,
    onClose: () -> Unit,
    onRestore: (TrashItem) -> Unit,
    onDelete: (TrashItem) -> Unit,
) {
    BackHandler(onBack = onClose)

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    val viewerBackdrop = rememberLayerBackdrop()
    val tint = MaterialTheme.colorScheme.onSurface
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val detailsThresholdPx = with(density) { 80.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    val liftPx = with(density) { (LocalWindowInfo.current.containerSize.height.dp * 0.22f).toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val pagerModifier = if (style.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(viewerBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            val isActive = page == pagerState.currentPage
            val liftOffset by animateFloatAsState(
                targetValue = if (showDetails && isActive) -liftPx else 0f,
                animationSpec = tween(durationMillis = 280),
                label = "trash-details-lift",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = dragOffsetY.value + liftOffset }
                    .pointerInput(isActive, item.id) {
                        if (!isActive) return@pointerInput
                        var totalDy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDy = 0f },
                            onVerticalDrag = { change, delta ->
                                totalDy += delta
                                if (delta > 0f || dragOffsetY.value > 0f) {
                                    change.consume()
                                    scope.launch {
                                        dragOffsetY.snapTo(
                                            (dragOffsetY.value + delta).coerceAtLeast(0f),
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                when {
                                    dragOffsetY.value >= dismissThresholdPx -> onClose()
                                    totalDy <= -detailsThresholdPx -> {
                                        showDetails = true
                                        scope.launch { dragOffsetY.animateTo(0f) }
                                    }
                                    else -> scope.launch { dragOffsetY.animateTo(0f) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragOffsetY.animateTo(0f) }
                            },
                        )
                    },
            ) {
                if (item.isVideo) {
                    VideoPlayerContent(
                        uri = item.uri,
                        isActive = isActive,
                        controlsVisible = chromeVisible,
                        onToggleControls = { chromeVisible = !chromeVisible },
                        style = style,
                        actionsSlot = if (isActive) {
                            {
                                TrashActionRow(
                                    onRestore = { onRestore(item) },
                                    onDelete = { onDelete(item) },
                                    onDetails = { showDetails = true },
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableAsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = item.uri,
                        contentDescription = null,
                        state = rememberZoomableImageState(),
                        onClick = { chromeVisible = !chromeVisible },
                    )
                }
            }
        }

        val current = items.getOrNull(pagerState.currentPage)
        if (chromeVisible && current != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = onClose,
                    contentDescription = stringResource(R.string.action_back),
                    style = style,
                    backdrop = viewerBackdrop,
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowLeft,
                        contentDescription = null,
                        tint = tint,
                    )
                }
            }

            if (!current.isVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .trashGlass(style, viewerBackdrop)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IslandAction(
                        icon = PhosphorIcons.Bold.ArrowClockwise,
                        label = stringResource(R.string.action_restore),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onRestore(current) },
                    )
                    IslandAction(
                        icon = PhosphorIcons.Bold.Trash,
                        label = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { onDelete(current) },
                    )
                    IslandAction(
                        icon = PhosphorIcons.Bold.Info,
                        label = stringResource(R.string.action_details),
                        tint = tint,
                        onClick = { showDetails = true },
                    )
                }
            }
        }

        if (showDetails && current != null) {
            TrashDetailsSheet(
                item = current,
                loadDetails = loadDetails,
                onDismiss = { showDetails = false },
            )
        }
    }
}

/**
 * The action row for a VIDEO in Trash, mounted as [VideoPlayerContent]'s
 * actionsSlot so the player controls + actions form ONE island. Only the 2 main
 * actions (Restore/Delete) + a Details button (because there are only 2 actions below).
 */
@Composable
private fun TrashActionRow(
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandAction(
            icon = PhosphorIcons.Bold.ArrowClockwise,
            label = stringResource(R.string.action_restore),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onRestore,
        )
        IslandAction(
            icon = PhosphorIcons.Bold.Trash,
            label = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
        IslandAction(
            icon = PhosphorIcons.Bold.Info,
            label = stringResource(R.string.action_details),
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onDetails,
        )
    }
}

/** Detail panel for a Trash item (opened via swipe up / the Details button). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashDetailsSheet(
    item: TrashItem,
    loadDetails: suspend (String) -> MediaDetails?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var details by remember(item.id) { mutableStateOf<MediaDetails?>(null) }
    LaunchedEffect(item.id) {
        details = loadDetails(item.uri)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.action_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DetailRow(stringResource(R.string.detail_type), if (item.isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_photo))
            if (item.isVideo) {
                DetailRow(stringResource(R.string.detail_duration), formatVideoDuration(item.durationMs))
            }
            val d = details
            if (d != null) {
                DetailRow(stringResource(R.string.detail_size), formatFileSize(d.sizeBytes, stringResource(R.string.value_unknown)))
                DetailRow(stringResource(R.string.detail_dimensions), stringResource(R.string.dimensions_format, d.width, d.height))
                if (d.relativePath.isNotBlank()) {
                    DetailRow(stringResource(R.string.detail_folder), d.relativePath)
                }
            }
            DetailRow(stringResource(R.string.detail_deleted), formatTrashedDate(item.trashedAt, stringResource(R.string.value_unknown)))
            DetailRow(stringResource(R.string.trash_auto_delete_in), stringResource(R.string.trash_days_left, daysLeftCount(item.trashedAt)))
        }
    }
}

/** A single label + value row in the detail panel. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/* --------------------------------------------------------------------------
 * Glyphs & format helpers
 * ------------------------------------------------------------------------ */

/** Centang manual (hindari dependency ikon check). */
@Composable
private fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(
            color = color,
            start = Offset(w * 0.18f, h * 0.55f),
            end = Offset(w * 0.42f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.78f),
            end = Offset(w * 0.82f, h * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Manual cross (for Cancel). */
@Composable
private fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(
            color = color,
            start = Offset(w * 0.25f, h * 0.25f),
            end = Offset(w * 0.75f, h * 0.75f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.75f, h * 0.25f),
            end = Offset(w * 0.25f, h * 0.75f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** "Select" icon (enter selection mode): a rounded square + a checkmark inside. */
@Composable
private fun SelectGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        val corner = w * 0.22f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.32f, h * 0.52f),
            end = Offset(w * 0.45f, h * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.45f, h * 0.66f),
            end = Offset(w * 0.70f, h * 0.34f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** "Select none" icon: an empty rounded square (outline only, no fill). */
@Composable
private fun SquareGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        val corner = w * 0.22f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
    }
}

/** Video duration m:ss / h:mm:ss (same as GalleryGridScreen). */
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

/** Days left before auto-delete (30 days since trashed). E.g. "12d". */
private fun daysLeftCount(trashedAtMs: Long): Long {
    val diffMs = (System.currentTimeMillis() - trashedAtMs).coerceAtLeast(0L)
    val days = diffMs / 86_400_000L
    return (30L - days).coerceAtLeast(0L)
}

/** Byte -> string ramah baca (B/KB/MB/GB/TB, basis 1024). */
private fun formatFileSize(bytes: Long, unknown: String): String {
    if (bytes <= 0L) return unknown
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

/** Epoch millis -> a human-readable local date-time (when the item was trashed). */
private fun formatTrashedDate(epochMillis: Long, unknown: String): String {
    if (epochMillis <= 0L) return unknown
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
