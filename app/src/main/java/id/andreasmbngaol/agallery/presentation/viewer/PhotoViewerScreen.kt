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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.usesLiveBackdrop
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.MediaType
import id.andreasmbngaol.agallery.domain.model.bucketAlbumKey
import id.andreasmbngaol.agallery.domain.model.mediaScopeFromKey
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Viewer full-screen ala Google Photos:
 * - Swipe antar media (HorizontalPager), pinch-to-zoom (Telephoto), video pakai
 *   [VideoPlayerContent].
 * - "Chrome" (top bar + action bar + kontrol video) di-toggle dgn tap layar.
 *   Statusnya global & persist antar halaman DAN config change (chromeVisible
 *   via rememberSaveable).
 * - Semua tombol pakai **liquid glass** (tergantung setting edge-effect, sama
 *   seperti nav bar gallery). Top bar: Back (kiri) + Info (kanan). Foto: action
 *   bar = 2 island [Share · Delete · Favorite] + More. Video: kontrol + aksi
 *   digabung jadi 1 island (kontrol di atas, aksi di bawah).
 * - Tap Trash -> konfirmasi pindah ke Trash. Tahan Trash -> hapus permanen
 *   (konfirmasi diserahkan ke dialog sistem Android, tanpa dialog internal).
 * - Swipe-down menutup; swipe-up / tombol Info membuka panel detail & media
 *   terangkat ke atas.
 */
@Composable
fun PhotoViewerScreen(
    initialIndex: Int,
    sortOrder: GallerySortOrder,
    onBack: () -> Unit,
    albumKey: String? = null,
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

    // Gaya komponen (Solid/Frosted/Glass) dari Settings. Hanya GLASS (refraction
    // live) yang meng-capture backdrop tiap frame; FROSTED/SOLID hemat GPU.
    // Sudah di-resolve (GLASS -> FROSTED di perangkat < API 33).
    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    // Backdrop dari konten media (pager) -> dibiaskan top bar & island foto.
    val backdrop = rememberLayerBackdrop()

    // Chrome global -> persist antar halaman & rotasi.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemoveMeta by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }
    var albumPickerMode by remember { mutableStateOf<AlbumPickerMode?>(null) }

    // Consent hapus permanen (system delete dialog).
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }
    // Consent tulis (rename/move file non-owned).
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
    // True saat halaman aktif sedang di-zoom -> matikan capture backdrop.
    var currentPageZoomed by remember { mutableStateOf(false) }

    // Latar viewer mengikuti tema (bukan hard-coded hitam). Di light-mode
    // akan terang, di dark-mode gelap. Saat user swipe-down utk dismiss,
    // opacity turun bertahap supaya konten di belakang mulai terlihat.
    val viewerBackground = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Dim latar dibaca di DRAW phase (drawBehind), bukan composition,
            // supaya swipe-down tak memicu recompose seluruh screen tiap frame.
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
            // Item terakhir baru saja di-trash/hapus -> keluar dari viewer.
            LaunchedEffect(Unit) { onBack() }
            return@Box
        }

        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, items.size - 1),
            pageCount = { items.size },
        )
        val currentItem = items.getOrNull(pagerState.currentPage)

        // QR Detection (1.7.0): scan foto aktif setelah halaman diam ~600ms
        // (debounce). Ganti halaman -> effect lama dibatalkan otomatis, hasil
        // di-cache di ViewModel jadi tak scan ulang saat balik ke foto yg sama.
        LaunchedEffect(currentItem?.id) {
            val item = currentItem ?: return@LaunchedEffect
            if (item.type != MediaType.IMAGE) return@LaunchedEffect
            delay(600.milliseconds)
            viewModel.detectQr(item)
        }

        // Pager jadi SUMBER backdrop yang dibiaskan glass (kalau didukung).
        // PENTING (perf): backdrop di-capture HANYA saat media benar-benar diam
        // -- chrome tampil, tak ada swipe-dismiss, tak sedang geser antar
        // halaman, dan tak di-zoom. Saat interaksi, capture dimatikan supaya
        // tidak ada render layer foto full-res tiap frame (biang utama lag).
        // Glass tetap menampilkan snapshot terakhir yang ter-capture.
        val captureBackdrop by remember(componentStyle) {
            derivedStateOf {
                componentStyle.drawsBackdrop() &&
                    chromeVisible &&
                    (
                        // GLASS: TETAP capture live walau lagi swipe/zoom -> kaca
                        // ngikut gerakan (tak lagi "turun" jadi frosted saat swipe).
                        componentStyle.usesLiveBackdrop() ||
                            // FROSTED: bekukan snapshot saat interaksi -> justru INI
                            // tampilan frosted yang diinginkan (kaca buram saat swipe)
                            // sekaligus hemat GPU.
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
            // Untuk video aktif: baris aksi ditaruh di dalam island video gabungan.
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
            // Foto: action bar 2 island di bawah. Video: aksi sudah menyatu di
            // island video gabungan (lihat videoActions), jadi tak digambar lagi.
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

            // Chip QR (pojok kanan-bawah, di atas action bar). Hanya tampil
            // saat chrome/tombol tampil & foto punya QR terdeteksi.
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

            if (showDetails) {
                MediaDetailsSheet(
                    item = item,
                    loadDetails = viewModel::loadDetails,
                    onDismiss = { showDetails = false },
                    style = componentStyle,
                    backdrop = backdrop,
                    // Hapus metadata hanya untuk foto (video di-skip).
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
    videoActions: (@Composable () -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val imageState = rememberZoomableImageState()
    val isVideo = item.type == MediaType.VIDEO

    // Lapor status zoom halaman aktif ke screen (utk gating capture backdrop).
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

    // Saat panel detail terbuka, media diangkat ke atas supaya tetap kelihatan
    // di atas sheet (perilaku Google Photos). Hanya untuk halaman aktif.
//    val configuration = LocalConfiguration.current
    val liftPx = with(density) { (LocalWindowInfo.current.containerSize.height.dp * 0.22f).toPx() }
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
        Box(modifier = Modifier.fillMaxSize().then(sharedModifier)) {
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

/** Bagikan media via ACTION_SEND. */
private fun shareMedia(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifEmpty { if (item.type == MediaType.VIDEO) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.uri.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}

/** Set as wallpaper / contact photo dll via ACTION_ATTACH_DATA. */
private fun setAsMedia(context: Context, item: MediaItem) {
    val mime = item.mimeType.ifEmpty { "image/*" }
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri.toUri(), mime)
        putExtra("mimeType", mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_set_as)))
}

/** Buka di aplikasi lain via ACTION_VIEW. */
private fun openWithMedia(context: Context, item: MediaItem) {
    val mime = item.mimeType.ifEmpty { if (item.type == MediaType.VIDEO) "video/*" else "image/*" }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri.toUri(), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_open_with)))
}
