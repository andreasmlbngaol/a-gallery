package id.andreasmbngaol.agallery.presentation.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.positionChange
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.usesLiveBackdrop
import id.andreasmbngaol.agallery.domain.model.album.bucketAlbumKey
import id.andreasmbngaol.agallery.domain.model.album.mediaScopeFromKey
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.presentation.ai.AiSheet
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Google Photos-style full-screen viewer:
 * - Swipe between media (HorizontalPager), pinch-to-zoom (Telephoto), video uses
 *   [VideoPlayerContent].
 * - The "chrome" (top bar + action bar + video controls) is toggled by tapping the
 *   screen. Its state is global & persists across pages AND config changes
 *   (chromeVisible via rememberSaveable).
 * - All buttons use **liquid glass** (depending on the edge-effect setting, same
 *   as the gallery nav bar). Top bar: Back (left) + Info (right). Photo: action
 *   bar = 2 islands [Share · Delete · Favorite] + More. Video: controls + actions
 *   combined into 1 island (controls on top, actions below).
 * - Tap Trash -> confirm move to Trash. Hold Trash -> permanent delete
 *   (confirmation deferred to the Android system dialog, no internal dialog).
 * - Swipe down to close; swipe up / the Info button opens the detail panel & the
 *   media lifts upward.
 */
@Composable
fun PhotoViewerScreen(
    mediaId: Long,
    initialIndex: Int,
    sortOrder: GallerySortOrder,
    onBack: () -> Unit,
    albumKey: String? = null,
    onOpenBackgroundRemover: (mediaUri: String, displayName: String) -> Unit = { _, _ -> },
    onOpenImageUpscale: (mediaUri: String, displayName: String) -> Unit = { _, _ -> },
    onOpenFaceRestore: (mediaUri: String, displayName: String) -> Unit = { _, _ -> },
    viewModel: PhotoViewerViewModel = koinViewModel(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val scope = remember(albumKey) { albumKey?.let { mediaScopeFromKey(it) } ?: MediaScope.Camera }
    LaunchedEffect(sortOrder, albumKey) { viewModel.setParams(sortOrder, scope) }

    val media by viewModel.media.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val qrDetections by viewModel.qrDetections.collectAsState()
    val liftState by viewModel.liftState.collectAsState()
    val haptics = LocalHapticFeedback.current
    var liftOffset by remember { mutableStateOf(Offset.Zero) }
    BackHandler(enabled = liftState !is SubjectLiftState.Idle) { viewModel.dismissLift() }

    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    val backdrop = rememberLayerBackdrop()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemoveMeta by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }
    var albumPickerMode by remember { mutableStateOf<AlbumPickerMode?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onWriteConsentGranted()
        } else {
            viewModel.onWriteConsentDenied()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteRequests.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(Unit) {
        viewModel.writeRequests.collect { sender ->
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var currentPageZoomed by remember { mutableStateOf(false) }

    val viewerBackground = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = 1f - (dragOffsetY.value / dismissThresholdPx).coerceIn(0f, 1f) * 0.6f
                drawRect(color = viewerBackground, alpha = a)
            },
    ) {
        val items = media
        if (items == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onBack() }
            return@Box
        }

        // Resolve the starting page by the tapped item's STABLE id, not by the
        // grid position. The grid (paging) and the viewer build their lists
        // independently, so a raw index can land on a different photo. Fall back
        // to the positional index only when the id isn't present.
        val initialPage = remember(items, mediaId) {
            items.indexOfFirst { it.id == mediaId }
                .takeIf { it >= 0 }
                ?: initialIndex.coerceIn(0, items.size - 1)
        }
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { items.size },
        )
        val currentItem = items.getOrNull(pagerState.currentPage)

        // Defense-in-depth for the "tapped A but opened B" case: if the first
        // media emission did not yet contain the tapped item (so the pager fell
        // back to a positional index), realign to the item's STABLE id the first
        // time it appears. Runs once, so it never fights manual swipes.
        var didAlignToTapped by remember(mediaId) { mutableStateOf(false) }
        LaunchedEffect(items, mediaId) {
            if (didAlignToTapped) return@LaunchedEffect
            val target = items.indexOfFirst { it.id == mediaId }
            if (target >= 0) {
                didAlignToTapped = true
                if (target != pagerState.currentPage) pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(currentItem?.id) {
            val item = currentItem ?: return@LaunchedEffect
            if (item.type != MediaType.IMAGE) return@LaunchedEffect
            delay(600.milliseconds)
            viewModel.detectQr(item)
        }

        val captureBackdrop by remember(componentStyle) {
            derivedStateOf {
                componentStyle.drawsBackdrop() &&
                    chromeVisible &&
                    (
                        componentStyle.usesLiveBackdrop() ||
                            (
                                !pagerState.isScrollInProgress &&
                                    !currentPageZoomed &&
                                    dragOffsetY.value == 0f
                            )
                    )
            }
        }
        val pagerModifier =
            if (captureBackdrop) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            key = { page -> items[page].id },
        ) { page ->
            val item = items[page]
            val isActive = page == pagerState.currentPage
            val videoActions: (@Composable () -> Unit)? =
                if (item.type == MediaType.VIDEO) {
                    {
                        ViewerVideoActionRow(
                            isFavorite = item.id in favoriteIds,
                            onShare = { shareMedia(context, item) },
                            onFavorite = { viewModel.onToggleFavorite(item.id, item.id !in favoriteIds) },
                            onTrashTap = { showTrashConfirm = true },
                            onHoldDelete = { pendingDeleteUri = item.uri },
                            onRename = { showRename = true },
                            onSetAsCover = {
                                viewModel.setAlbumCover(albumKey ?: bucketAlbumKey(item.bucketId), item.id)
                            },
                            onOpenWith = { openWithMedia(context, item) },
                            onCopy = {
                                viewModel.loadAlbums()
                                albumPickerMode = AlbumPickerMode.COPY
                            },
                            onMove = {
                                viewModel.loadAlbums()
                                albumPickerMode = AlbumPickerMode.MOVE
                            },
                            onDelete = { pendingDeleteUri = item.uri },
                        )
                    }
                } else {
                    null
                }
            PhotoViewerPage(
                item = item,
                isActive = isActive,
                chromeVisible = chromeVisible,
                onToggleChrome = { chromeVisible = !chromeVisible },
                dragOffsetY = dragOffsetY,
                dismissThresholdPx = dismissThresholdPx,
                detailsOpen = showDetails,
                onDismiss = onBack,
                onOpenDetails = { showDetails = true },
                style = componentStyle,
                onZoomChange = { currentPageZoomed = it },
                liftEnabled = item.type == MediaType.IMAGE,
                onLiftStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    liftOffset = Offset.Zero
                    viewModel.liftSubject(item)
                },
                onLiftMove = { dx, dy -> liftOffset += Offset(dx, dy) },
                onLiftRelease = {
                    if (viewModel.liftState.value is SubjectLiftState.Processing) {
                        viewModel.dismissLift()
                    }
                },
                videoActions = videoActions,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                onBack = onBack,
                onInfo = { showDetails = true },
                style = componentStyle,
                backdrop = backdrop,
            )
        }

        currentItem?.let { item ->
            val isVideo = item.type == MediaType.VIDEO
            if (!isVideo) {
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ViewerActionBar(
                        isFavorite = item.id in favoriteIds,
                        style = componentStyle,
                        backdrop = backdrop,
                        onAiClick = { showAiSheet = true },
                        onShare = { shareMedia(context, item) },
                        onFavorite = { viewModel.onToggleFavorite(item.id, item.id !in favoriteIds) },
                        onTrashTap = { showTrashConfirm = true },
                        onHoldDelete = { pendingDeleteUri = item.uri },
                        onRename = { showRename = true },
                        onSetAs = { setAsMedia(context, item) },
                        onConvertFormat = { showConvert = true },
                        onSetAsCover = {
                            viewModel.setAlbumCover(albumKey ?: bucketAlbumKey(item.bucketId), item.id)
                        },
                        onOpenWith = { openWithMedia(context, item) },
                        onCopy = {
                            viewModel.loadAlbums()
                            albumPickerMode = AlbumPickerMode.COPY
                        },
                        onMove = {
                            viewModel.loadAlbums()
                            albumPickerMode = AlbumPickerMode.MOVE
                        },
                        onDelete = { pendingDeleteUri = item.uri },
                    )
                }
            }

            val qrResults = qrDetections[item.id].orEmpty()
            if (!isVideo && qrResults.isNotEmpty()) {
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 92.dp),
                ) {
                    QrDetectedChip(
                        count = qrResults.size,
                        style = componentStyle,
                        backdrop = backdrop,
                        onClick = { showQrSheet = true },
                    )
                }
            }
            if (showQrSheet && qrResults.isNotEmpty()) {
                QrResultSheet(
                    results = qrResults,
                    onDismiss = { showQrSheet = false },
                )
            }

            if (showAiSheet && !isVideo) {
                AiSheet(
                    onRemoveBackground = {
                        showAiSheet = false
                        onOpenBackgroundRemover(item.uri, item.displayName)
                    },
                    onUpscaleImage = {
                        showAiSheet = false
                        onOpenImageUpscale(item.uri, item.displayName)
                    },
                    onRestoreFaces = {
                        showAiSheet = false
                        onOpenFaceRestore(item.uri, item.displayName)
                    },
                    onDismiss = { showAiSheet = false },
                )
            }

            if (showDetails) {
                MediaDetailsSheet(
                    item = item,
                    loadDetails = viewModel::loadDetails,
                    onDismiss = { showDetails = false },
                    style = componentStyle,
                    backdrop = backdrop,
                    onRemoveMetadata = if (item.type == MediaType.IMAGE) {
                        {
                            showDetails = false
                            showRemoveMeta = true
                        }
                    } else {
                        null
                    },
                )
            }
            if (showRename) {
                RenameDialog(
                    initialName = item.displayName,
                    onConfirm = { newName ->
                        viewModel.rename(item.uri, newName)
                        showRename = false
                    },
                    onDismiss = { showRename = false },
                )
            }
            if (showRemoveMeta) {
                RemoveMetadataDialog(
                    onConfirm = { categories, saveAsCopy ->
                        viewModel.removeMetadata(item, categories, saveAsCopy)
                        showRemoveMeta = false
                    },
                    onDismiss = { showRemoveMeta = false },
                )
            }
            if (showConvert) {
                ConvertFormatDialog(
                    currentMime = item.mimeType,
                    onConfirm = { target, quality, deleteOriginal ->
                        viewModel.convertFormat(item, target, quality, deleteOriginal)
                        showConvert = false
                    },
                    onDismiss = { showConvert = false },
                )
            }
            if (showTrashConfirm) {
                MoveToTrashConfirmDialog(
                    onConfirm = {
                        viewModel.moveToTrash(item)
                        showTrashConfirm = false
                    },
                    onDismiss = { showTrashConfirm = false },
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
            albumPickerMode?.let { mode ->
                AlbumThumbnailPickerDialog(
                    title = if (mode == AlbumPickerMode.COPY) "Copy to album" else "Move to album",
                    albums = albums,
                    onPick = { name ->
                        if (mode == AlbumPickerMode.COPY) {
                            viewModel.copyToAlbum(item, name)
                        } else {
                            viewModel.moveToAlbum(item.uri, name)
                        }
                        albumPickerMode = null
                    },
                    onDismiss = { albumPickerMode = null },
                )
            }
        }

        SubjectLiftOverlay(
            state = liftState,
            offset = liftOffset,
            style = componentStyle,
            backdrop = backdrop,
            onDrag = { liftOffset += it },
            onCopy = {
                (liftState as? SubjectLiftState.Lifted)?.let { copyCutout(context, it.resultPath) }
            },
            onShare = {
                (liftState as? SubjectLiftState.Lifted)?.let { shareCutout(context, it.resultPath) }
            },
            onDismiss = { viewModel.dismissLift() },
        )
    }
}

private enum class AlbumPickerMode { COPY, MOVE }

@Composable
private fun PhotoViewerPage(
    item: MediaItem,
    isActive: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    dragOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    detailsOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    style: ComponentStyle,
    onZoomChange: (Boolean) -> Unit,
    liftEnabled: Boolean,
    onLiftStart: () -> Unit,
    onLiftMove: (Float, Float) -> Unit,
    onLiftRelease: () -> Unit,
    videoActions: (@Composable () -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val imageState = rememberZoomableImageState()
    val isVideo = item.type == MediaType.VIDEO

    LaunchedEffect(isActive, imageState) {
        if (!isActive) {
            onZoomChange(false)
            return@LaunchedEffect
        }
        snapshotFlow { (imageState.zoomableState.zoomFraction ?: 0f) > 0.01f }
            .collect { onZoomChange(it) }
    }
    val density = LocalDensity.current
    val detailsThresholdPx = with(density) { 80.dp.toPx() }

    val liftPx = LocalWindowInfo.current.containerSize.height * 0.22f
    val liftOffset by animateFloatAsState(
        targetValue = if (detailsOpen && isActive) -liftPx else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "details-lift",
    )

    val canDismiss by remember(isActive, isVideo) {
        derivedStateOf {
            isActive && (isVideo || (imageState.zoomableState.zoomFraction ?: 0f) < 0.01f)
        }
    }
    val sharedModifier = if (isActive) {
        Modifier.sharedPhotoElement(key = "photo-${item.id}")
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffsetY.value + liftOffset }
            .pointerInput(canDismiss) {
                if (!canDismiss) return@pointerInput
                var totalDy = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDy = 0f },
                    onVerticalDrag = { change, delta ->
                        totalDy += delta
                        if (delta > 0f || dragOffsetY.value > 0f) {
                            change.consume()
                            val next = (dragOffsetY.value + delta).coerceAtLeast(0f)
                            scope.launch { dragOffsetY.snapTo(next) }
                        }
                    },
                    onDragEnd = {
                        when {
                            dragOffsetY.value >= dismissThresholdPx -> onDismiss()
                            totalDy <= -detailsThresholdPx -> {
                                onOpenDetails()
                                scope.launch { dragOffsetY.animateTo(0f) }
                            }
                            else -> scope.launch { dragOffsetY.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { dragOffsetY.animateTo(0f) } },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier)
                .pointerInput(item.id, liftEnabled) {
                    if (!liftEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var isLift = false
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) return@withTimeout
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeout
                                    if (!change.pressed) return@withTimeout
                                    if (change.isConsumed) return@withTimeout
                                    if ((change.position - down.position).getDistance() > slop) return@withTimeout
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            isLift = true
                        }
                        if (!isLift) return@awaitEachGesture
                        onLiftStart()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                change?.consume()
                                onLiftRelease()
                                break
                            }
                            val dc = change.positionChange()
                            if (dc != Offset.Zero) {
                                onLiftMove(dc.x, dc.y)
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            if (isVideo) {
                VideoPlayerContent(
                    uri = item.uri,
                    isActive = isActive,
                    controlsVisible = chromeVisible,
                    onToggleControls = onToggleChrome,
                    style = style,
                    actionsSlot = videoActions,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ZoomableAsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = item.uri,
                    contentDescription = item.displayName,
                    state = imageState,
                    onClick = { onToggleChrome() },
                )
            }
        }
    }
}

/** Share media via ACTION_SEND. */
private fun shareMedia(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifEmpty { if (item.type == MediaType.VIDEO) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.uri.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}

/** Set as wallpaper / contact photo etc. via ACTION_ATTACH_DATA. */
private fun setAsMedia(context: Context, item: MediaItem) {
    val mime = item.mimeType.ifEmpty { "image/*" }
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri.toUri(), mime)
        putExtra("mimeType", mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_set_as)))
}

/** Open in another app via ACTION_VIEW. */
private fun openWithMedia(context: Context, item: MediaItem) {
    val mime = item.mimeType.ifEmpty { if (item.type == MediaType.VIDEO) "video/*" else "image/*" }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri.toUri(), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_open_with)))
}


/** Wraps a cutout cache PNG as a shareable content:// uri via FileProvider. */
private fun cutoutUri(context: Context, resultPath: String): Uri? = try {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(resultPath))
} catch (_: Throwable) {
    null
}

/** Share a lifted cutout PNG via ACTION_SEND. */
private fun shareCutout(context: Context, resultPath: String) {
    val uri = cutoutUri(context, resultPath) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}

/** Copy a lifted cutout PNG to the clipboard as an image. */
private fun copyCutout(context: Context, resultPath: String) {
    val uri = cutoutUri(context, resultPath) ?: return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newUri(context.contentResolver, "cutout", uri)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.msg_lift_copied), Toast.LENGTH_SHORT).show()
}
