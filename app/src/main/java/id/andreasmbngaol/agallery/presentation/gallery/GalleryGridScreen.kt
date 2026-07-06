package id.andreasmbngaol.agallery.presentation.gallery

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.phosphoricons.bold.Trash
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import coil3.SingletonImageLoader
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnail
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import android.os.Build
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ceil
import kotlinx.coroutines.launch
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.Trash
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.CheckSquare
import com.adamglin.phosphoricons.bold.Square
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.bold.Copy
import com.adamglin.phosphoricons.bold.FolderSimple
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ImageSquare
import com.adamglin.phosphoricons.bold.WarningCircle
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.mediaScopeFromKey
import id.andreasmbngaol.agallery.domain.model.MediaType
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import id.andreasmbngaol.agallery.presentation.viewer.AlbumThumbnailPickerDialog
import id.andreasmbngaol.agallery.presentation.viewer.HoldToDeleteButton
import id.andreasmbngaol.agallery.presentation.viewer.MoveToTrashConfirmDialog
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Tinggi visual TopAppBar (sedikit di atas M3 default 64dp untuk
// mengakomodasi judul 26sp SemiBold + FilledTonalIconButton 44dp).
private val GalleryTopAppBarHeight = 72.dp

// Jarak maksimum konten "ikut ketarik" turun saat pull-to-refresh (iOS-style).
// Dipetakan dari distanceFraction: 1f (pas di ambang) = geser sejauh ini.
private val PullContentMaxOffset = 80.dp

// Diameter kontainer indikator pull-to-refresh.
private val PullIndicatorSize = 40.dp

// Ukuran (px) decode thumbnail grid — kecil & seragam biar ringan +
// cache-friendly (satu cache key untuk semua cell).
private const val GridThumbnailPx = 400
// Jendela prefetch (dalam satuan BARIS) per mode performa. Angka lebih besar =
// lebih banyak thumbnail dimuat lebih awal ke RAM sebelum masuk layar.
// first = baris DI DEPAN viewport, second = baris DI ATAS (untuk scroll balik).
private fun PerformanceMode.prefetchRows(): Pair<Int, Int> = when (this) {
    PerformanceMode.LOW -> 2 to 0
    PerformanceMode.BALANCED -> 6 to 2
    PerformanceMode.HIGH -> 12 to 4
}

// Fast-scroll scrollbar (grip di tepi kanan grid).
// Lebar area sentuh drag (lebih lebar dari thumb biar gampang ditangkap jari).
private val ScrollbarTouchWidth = 32.dp
// Lebar visual thumb (pill) — lebih tebal biar gampang dilihat & ditarik.
private val ScrollbarThumbWidth = 8.dp
// Lebar thumb saat sedang ditekan/di-drag — membesar biar gampang dipegang.
private val ScrollbarThumbDragWidth = 16.dp
// Tinggi thumb tetap, gaya "grip handle" (bukan proporsional).
private val ScrollbarThumbHeight = 52.dp
// Jarak thumb dari tepi kanan area sentuh.
private val ScrollbarEndPadding = 4.dp
// Inset seluruh strip scrollbar dari tepi kanan layar, supaya thumb tidak
// menempel ke edge (susah diraih + kadang memicu gesture "back" sistem).
private val ScrollbarEdgeInset = 4.dp

private val TitleDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private fun formatTitleDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(TitleDateFormatter)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Konten tab Gallery (grid foto).
 *
 * Di-host di dalam pager
 * [id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen]; floating bar
 * (Gallery/Albums, Settings, Sort) digambar oleh host itu, BUKAN di sini.
 * Dulu screen ini membungkus dirinya sendiri dengan `GalleryTabScaffold`.
 *
 * Preferensi (sortOrder, jumlah kolom, edge effect) dibaca dari
 * [GalleryViewModel] yang mem-persist ke DataStore, jadi reaktif terhadap
 * perubahan di layar Settings dan TETAP walau aplikasi ditutup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridScreen(
    onMediaClick: (mediaId: Long, index: Int, sortOrder: GallerySortOrder) -> Unit,
    onScrollStateChange: (Boolean) -> Unit = {},
    // Mode album: kalau di-set, top bar memakai judul statis ([staticTitle]) +
    // tombol Back ([onBack]) dan ruang bawah tak lagi menyisakan floating bar.
    staticTitle: String? = null,
    onBack: (() -> Unit)? = null,
    // Aktifkan multi-select (Copy/Move/Delete batch). Hanya dipakai layar
    // detail album; tab Gallery utama memakai default false.
    selectionEnabled: Boolean = false,
    albumKey: String? = null,
    viewModel: GalleryViewModel = koinViewModel(),
) {
    // Semua preferensi bersumber dari DataStore lewat VM (reaktif & persisten).
    val sortOrder by viewModel.sortOrder.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val chosenMode by viewModel.edgeEffectMode.collectAsState()
    val effectiveMode = rememberEffectiveEdgeEffectMode(chosenMode)
    // Gaya komponen (Solid/Frosted/Glass) untuk tombol & overlay. Terpisah dari
    // edge effect (yang cuma untuk scrim tepi layar). GLASS = refraction live.
    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    // Backdrop khusus top bar: menandai KONTEN grid (bukan overlay app bar)
    // sebagai sumber, supaya tombol Search membiaskan foto di belakangnya.
    val topBarBackdrop = rememberLayerBackdrop()

    val gridState = rememberLazyGridState()
    val items = viewModel.media.collectAsLazyPagingItems()

    // Laporkan status scroll grid ke host supaya floating nav bar FROSTED bisa
    // MEMBEKUKAN capture backdrop saat scroll (hemat GPU). GLASS tetap live.
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
    // Bottom = safeArea (nav bar) + tinggi floating bar. Grid tetap edge-to-
    // edge (fillMaxSize), item terakhir cukup padded supaya tidak ke-tutup
    // pill segmented bar.
    val gridContentPadding = PaddingValues(
        top = safeDrawingPadding.calculateTopPadding() + GalleryTopAppBarHeight,
        // Mode album (onBack != null) tak punya floating tab bar → cukup safe
        // area; mode galeri sisakan tinggi floating bar juga.
        bottom = safeDrawingPadding.calculateBottomPadding() +
            (if (onBack != null) 0.dp else FloatingTabBarHeight),
        start = safeDrawingPadding.calculateStartPadding(layoutDirection),
        end = safeDrawingPadding.calculateEndPadding(layoutDirection),
    )

    val previewItem by viewModel.previewItem.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    // ---- Multi-select batch (aktif hanya bila selectionEnabled = detail album) ----
    val albums by viewModel.albums.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Long, MediaItem>() }
    // Copy/Move/Delete berlaku di SEMUA album KECUALI album khusus.
    // Album khusus = Recent (AllMedia), Videos (AllVideos), Favorites -> hanya Delete.
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

    // Launcher untuk konfirmasi hapus scoped-storage (dialog sistem, API 30+).
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
    // Consent SATU-kali untuk batch move (API 30+); saat disetujui, VM lanjut.
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

    // Background (galeri) di-blur saat preview long-press aktif.
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
                            // Beri jarak dari tombol back ketika mode album (onBack != null);
                            // di mode gallery utama padding kiri ekstra tidak diperlukan
                            // karena tak ada navigationIcon.
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
                        // Extra kiri (16.dp) supaya tombol back TIDAK menempel
                        // ke tepi layar dan ada nafas dgn safe-area.
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
            // Capture backdrop HANYA saat overlay preview aktif (grid diam) supaya
            // scroll biasa tak kena render layer. GLASS & FROSTED sama-sama kaca;
            // SOLID / < API 33 tak pernah nempel modifier ini (drawsBackdrop=false).
            Modifier.fillMaxSize().layerBackdrop(topBarBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = gridBackdropModifier) {
            GalleryPagingContent(
                items = items,
                gridState = gridState,
                contentPadding = gridContentPadding,
                sourceModifier = sourceModifier,
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
        } // tutup Box background (blur)

        var pendingDeleteUri by remember { mutableStateOf<String?>(null) }

        previewItem?.let { preview ->
            PhotoPreviewOverlay(
                item = preview,
                backdrop = topBarBackdrop,
                style = componentStyle,
                isFavorite = preview.id in favoriteIds,
                onDismiss = { viewModel.dismissPreview() },
                onFavoriteClick = {
                    // Toggle: kirim kebalikan dari status favorit saat ini.
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

        // ---------- Island multi-select (hanya di detail album) ----------
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
                    // SATU-SATUNYA pemicu multi-select. Long-press item TETAP
                    // membuka menu preview (Favorite / Trash / Delete) seperti semula.
                    IslandAction(
                        icon = PhosphorIcons.Bold.CheckSquare,
                        label = stringResource(R.string.action_select),
                        tint = tint,
                        onClick = { selectionMode = true },
                    )
                } else {
                    // Select all / Select none (gaya & warna sama dgn island Trash).
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
                        // Copy & Move di SEMUA album kecuali khusus (Recent/Videos/Favorites).
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
                        // Tap = ke Trash (konfirmasi ringan); tahan = hapus permanen
                        // (animasi gradient sama seperti di photo/video viewer).
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

            // Picker album (grid thumbnail) utk Copy/Move batch.
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

    } // tutup Box root
}

/** Mode picker album utk aksi batch di island seleksi. */
private enum class BatchAlbumMode { COPY, MOVE }

/**
 * Modifier kapsul liquid-glass (pola identik dgn island Trash/PhotoViewer).
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

/** Satu aksi di island: ikon Phosphor saja (label utk contentDescription). */
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

/** Lingkaran check: kosong (outline putih) atau terisi primary + centang. */
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
    sourceModifier: Modifier,
    gridColumns: Int,
    performanceMode: PerformanceMode,
    onMediaClick: (Long, Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
    selectionMode: Boolean = false,
    isSelected: (Long) -> Boolean = { false },
    onToggleSelect: (MediaItem) -> Unit = {},
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> {
            LoadingState(contentPadding = contentPadding)
        }

        refresh is LoadState.Error && items.itemCount == 0 -> {
            // Error awal juga dibungkus pull-to-refresh: selain tombol Retry,
            // user bisa tarik-untuk-refresh seperti biasa.
            GalleryPullToRefresh(
                items = items,
                contentPadding = contentPadding,
                sourceModifier = sourceModifier,
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

        refresh is LoadState.NotLoading && items.itemCount == 0 -> {
            // PENTING: empty state pun dibungkus pull-to-refresh. Ini kejadian
            // tepat setelah izin baru diberikan — galeri masih kosong sesaat,
            // dan user harus bisa menarik untuk memicu reload MediaStore.
            GalleryPullToRefresh(
                items = items,
                contentPadding = contentPadding,
                sourceModifier = sourceModifier,
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
            sourceModifier = sourceModifier,
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
 * Pembungkus pull-to-refresh gaya iOS yang dipakai BARENG oleh grid, empty
 * state, dan error state — supaya user tetap bisa tarik-untuk-refresh WALAU
 * galeri masih kosong (mis. tepat setelah izin diberikan, konten belum sempat
 * ke-load).
 *
 * [content] menerima sebuah Modifier `translationY` ([contentOffset]) yang
 * HARUS dipasang ke elemen scrollable-nya, supaya konten ikut ketarik turun
 * mengikuti jari dan indikator muncul di celah atasnya.
 *
 * Catatan penting: elemen di dalam [content] HARUS scrollable
 * (LazyVerticalGrid untuk grid; LazyColumn untuk empty/error) supaya
 * nested-scroll pull bisa ke-detect meski isinya sedikit/kosong. Column biasa
 * tidak akan memicu pull-to-refresh.
 *
 * Beda sama default Material3 (indikator ngambang, konten diam): di sini
 * KONTEN ikut ketarik turun lewat `graphicsLayer`
 * `translationY = distanceFraction * maxOffset`, indikator MENGAMBANG di
 * TENGAH celah yang kebuka. Urutannya:
 *   1. Jari narik → konten geser turun, indikator muncul di celah atasnya.
 *   2. Lepas setelah lewat ambang → `items.refresh()` jalan, spinner muter.
 *   3. Refresh selesai (`isRefreshing` false) → state balik ke hidden, konten
 *      naik lagi & indikator fade-out. Semua otomatis dari PullToRefreshBox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryPullToRefresh(
    items: LazyPagingItems<MediaItem>,
    contentPadding: PaddingValues,
    sourceModifier: Modifier,
    content: @Composable (contentOffset: Modifier) -> Unit,
) {
    val isRefreshing = items.loadState.refresh is LoadState.Loading

    // State di-share antara PullToRefreshBox, indikator, dan geseran konten
    // supaya semuanya baca distanceFraction yang sama.
    val pullState = rememberPullToRefreshState()

    // Konversi dp → px sekali di composition. `density` dipakai bareng buat
    // jarak geser konten, inset atas (di bawah app bar), dan separuh tinggi
    // indikator (buat nge-tengah-in di celah).
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
            .then(sourceModifier),
        indicator = {
            // Indikator MENGAMBANG di TENGAH celah yang kebuka saat konten
            // ketarik turun — bukan nempel di pinggir gambar. Celah = dari
            // bawah app bar (topInset) sampai top konten yang kegeser
            // (topInset + fraction*maxOffset). Titik tengah = topInset +
            // (fraction*maxOffset)/2, dikurangi separuh tinggi indikator.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(PullIndicatorSize)
                    .graphicsLayer {
                        val fraction = pullState.distanceFraction.coerceAtLeast(0f)
                        translationY =
                            topInsetPx + (fraction * maxOffsetPx) / 2f - indicatorHalfPx
                        // Fade-in ngikutin tarikan; saat refreshing fraction ≈ 1
                        // jadi tetap keliatan penuh.
                        alpha = fraction.coerceIn(0f, 1f)
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        // Muter terus selama reload.
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        // Sebelum lepas: arc determinate ngisi sesuai tarikan.
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
        // Modifier geser konten (draw phase, tanpa recompose per-frame).
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
    sourceModifier: Modifier,
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
        sourceModifier = sourceModifier,
    ) { contentOffset ->
        val context = LocalContext.current

        // === Early loading (prefetch thumbnail) ===
        // Muat thumbnail beberapa baris DI DEPAN viewport ke cache sebelum
        // ke-scroll ke sana, supaya saat sampai gambar sudah siap dan tidak
        // nge-decode mendadak di tengah scroll (sumber patah/teleport).
        val imageLoader = SingletonImageLoader.get(context)
        val (rowsAhead, rowsBack) = performanceMode.prefetchRows()
        // Prefetch HANYA saat scroll berhenti (settle) & loop-nya di background
        // thread. Pas fling, decoder dibiarkan fokus ke thumbnail yang DI LAYAR;
        // jangan direbut kerjaan prefetch offscreen (itu yang dulu bikin mode
        // agresif malah patah-patah + numpuk enqueue di main thread tiap frame).
        LaunchedEffect(gridState, items, gridColumns, rowsAhead, rowsBack) {
            snapshotFlow { gridState.isScrollInProgress }
                .distinctUntilChanged()
                .collectLatest { scrolling ->
                    if (scrolling) return@collectLatest
                    // Jeda kecil biar benar-benar diam. Kalau user scroll lagi,
                    // collectLatest membatalkan blok ini (prefetch stale dibuang).
                    delay(120)
                    val info = gridState.layoutInfo.visibleItemsInfo
                    val firstVisible = info.firstOrNull()?.index ?: return@collectLatest
                    val lastVisible = info.lastOrNull()?.index ?: return@collectLatest
                    val lastIndex = items.itemCount - 1
                    if (lastIndex < 0) return@collectLatest
                    // Enqueue di Default dispatcher -> lepas dari main thread biar
                    // build request + peek tidak nyendat frame.
                    withContext(Dispatchers.Default) {
                        // Jendela prefetch: beberapa baris DI ATAS (scroll balik)
                        // & DI DEPAN viewport. Makin agresif mode -> jendela makin
                        // lebar -> makin banyak RAM dipakai. Karena cuma jalan pas
                        // settle, ini tidak lagi ganggu kelancaran fling.
                        val start = (firstVisible - gridColumns * rowsBack)
                            .coerceAtLeast(0)
                        val end = (lastVisible + gridColumns * rowsAhead)
                            .coerceAtMost(lastIndex)
                        for (i in start..end) {
                            // Lewati item yang sedang di layar (sudah dirender).
                            if (i in firstVisible..lastVisible) continue
                            // peek(): baca item yang sudah ada tanpa trigger load
                            // hint paging. Yang belum ke-load dilewati.
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
                    // Slot placeholder saat halaman belum ter-load
                    // (enablePlaceholders=true): pertahankan rasio 1:1 supaya
                    // posisi grid & proporsi scrollbar tetap akurat.
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
                                // Ambil thumbnail bawaan MediaStore (bukan decode
                                // file penuh) -> jauh lebih ringan & cepat.
                                .data(
                                    MediaStoreThumbnail(
                                        uri = item.uri,
                                        isVideo = item.type == MediaType.VIDEO,
                                    ),
                                )
                                // Ukuran seragam -> cache key sama utk semua cell.
                                .size(GridThumbnailPx, GridThumbnailPx)
                                // Tanpa crossfade: hindari animasi fade tiap cell
                                // muncul saat scroll cepat (salah satu sumber
                                // patah-patah).
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
 * Badge kecil di pojok thumbnail untuk item video: ikon play + durasi
 * (m:ss / h:mm:ss). Latar semi-transparan hitam biar tetap kebaca di atas
 * frame apa pun.
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

/** Format durasi video: m:ss, atau h:mm:ss kalau >= 1 jam. */
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
 * Empty state. Dibungkus [LazyColumn] (bukan Column biasa) + item
 * `fillParentMaxSize` supaya: (a) pesan tetap ke-tengah di viewport, dan
 * (b) kontainernya scrollable sehingga nested-scroll pull-to-refresh bisa
 * ke-trigger walau kontennya cuma sedikit.
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

/** Error state — scrollable (LazyColumn) supaya pull-to-refresh tetap jalan. */
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
 * Fling dengan sensitivitas lebih tinggi: velocity awal dikalikan supaya
 * lemparan scroll terasa lebih responsif & meluncur lebih jauh (mengurangi
 * kesan "nyendat" saat flick cepat). Delegasi ke fling default Compose.
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
 * Overlay preview saat foto ditahan (long-press): foto membesar ke aspect
 * ratio ASLI-nya (dibatasi maxWidth/maxHeight biar tak keluar layar),
 * background galeri di-blur oleh pemanggil, dan muncul menu konteks
 * (liquid glass) untuk 1 foto tsb.
 *
 * Tap area gelap / tombol back = tutup. Tap area konten tidak menutup.
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
            // Batas maksimum: foto tidak boleh melebihi ini walau aslinya besar.
            val maxImageWidth = maxWidth * 0.9f
            val maxImageHeight = maxHeight * 0.62f
            // Animasi "membesar": media tumbuh dari kecil ke penuh (spring),
            // TANPA fade opacity — sesuai permintaan (bukan fadeOut + scale).
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
                // Konsumsi tap di area konten supaya tidak ikut menutup overlay.
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
                    // Fit = pertahankan aspect ratio asli, muat dalam batas.
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
 * Tombol "Back" bulat 40dp yang gaya-nya mengikuti [ComponentStyle] aktif:
 *  - SOLID   : lingkaran hitam semi-transparan (drawsBackdrop=false).
 *  - FROSTED : haze putih tebal (drawBackdrop tanpa blur/lens).
 *  - GLASS   : blur + lens Kyant untuk efek kaca cair.
 *
 * Dipakai di TopBar Gallery HANYA saat [onBack] non-null (mode album-detail).
 */
@Composable
private fun StyledCircleBackButton(
    style: ComponentStyle,
    backdrop: Backdrop,
    onClick: () -> Unit,
) {
    val size = 40.dp
    // Untuk mode SOLID (tanpa backdrop) di light-theme, lingkaran hitam +
    // ikon putih tetap kontras. Untuk FROSTED/GLASS, backdrop-nya diwarnai
    // dgn `Color.White.copy(...)` yg cocok utk dark tapi tak terlihat di
    // light mode -> pakai onBackground.copy supaya kontras di kedua tema.
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
                    // Pakai onBackground.copy alpha rendah supaya di light-mode
                    // lingkaran ini gelap-tembus, di dark-mode terang-tembus
                    // -> tetap terbaca di kedua tema.
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
            // SOLID: kontras kuat pakai onBackground.copy alpha rendah
            // (bukan Color.Black hard-coded) supaya adaptif thd tema.
            .background(onBg.copy(alpha = 0.16f))
    }

    Box(
        modifier = circleModifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Bold.ArrowLeft,
            contentDescription = stringResource(R.string.action_back),
            // Ikon ikut theme (onBackground): di light-mode gelap, di dark-mode
            // terang -- tidak lagi "selalu putih" (yg invisible di light mode).
            tint = onBg,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Menu konteks 1-foto dengan latar LIQUID GLASS (Kyant drawBackdrop; fallback
 * frosted untuk API < 33). Berisi 3 aksi berdampingan:
 *  - Favorite : toggle favorit (Room lokal). Ikon hati TERISI saat aktif.
 *  - Trash    : pindah ke Trash (soft-delete, bisa dipulihkan 30 hari).
 *  - Delete   : hapus PERMANEN dari device (dialog sistem scoped-storage).
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
    // Pill shape supaya konsisten dgn lens Capsule() di drawBackdrop.
    val menuShape = RoundedCornerShape(percent = 50)
    val deleteColor = Color(0xFFFF453A) // merah destructive gaya iOS
    val favoriteColor = Color(0xFFFF375F) // pink/merah muda utk hati aktif
    val neutralColor = Color.White

    val glassModifier = if (style.drawsBackdrop()) {
        Modifier
            .clip(menuShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    // Selalu blur backdrop di preview overlay supaya foto-foto lain
                    // di belakang TIDAK bocor tajam lewat kaca (khususnya FROSTED
                    // yg tanpa lens). Hasilnya latar yg tampak = blurry, konsisten
                    // dgn background overlay yg memang diblur. GLASS pakai blur
                    // lebih tipis karena sudah dikombinasi dgn lens.
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
 * Satu tombol aksi di [PhotoContextMenu]: ikon di atas, label kecil di bawah,
 * dgn area tap sendiri (rounded) supaya 3 aksi tidak saling tumpang tindih.
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
 * Scrollbar fast-scroll utk grid galeri: grip pill di tepi kanan yg muncul saat
 * scrolling / di-drag, lalu fade-out. Bisa DITARIK utk loncat cepat ke posisi
 * mana pun (mis. langsung ke paling bawah).
 *
 * Posisi thumb dihitung dari proporsi baris pertama yg tampak terhadap total
 * baris (`firstVisibleItemIndex / kolom`). `translationY` + `alpha` dibaca di
 * dalam `graphicsLayer` (draw phase) supaya update tiap frame TANPA recompose.
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

    // Cukup di-scroll kalau total item lebih banyak dari yang tampak.
    val scrollable by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.isNotEmpty() &&
                info.totalItemsCount > info.visibleItemsInfo.size
        }
    }

    // Progres 0..1 = baris pertama tampak / (total baris - baris tampak).
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
        // Thumb melebar saat ditekan/di-drag supaya gampang dipegang.
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
