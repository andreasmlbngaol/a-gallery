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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import com.adamglin.phosphoricons.regular.Trash
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import android.os.Build
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ImageSquare
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.WarningCircle
import id.andreasmbngaol.agallery.core.permission.MediaPermissionGate
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaType
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
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

// Judul default saat grid di posisi paling atas.
private const val DefaultTitle = "Gallery"

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
    onOpenSearch: () -> Unit = {},
    viewModel: GalleryViewModel = koinViewModel(),
) {
    // Semua preferensi bersumber dari DataStore lewat VM (reaktif & persisten).
    val sortOrder by viewModel.sortOrder.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val chosenMode by viewModel.edgeEffectMode.collectAsState()
    val effectiveMode = rememberEffectiveEdgeEffectMode(chosenMode)

    val liquidGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // Backdrop khusus top bar: menandai KONTEN grid (bukan overlay app bar)
    // sebagai sumber, supaya tombol Search membiaskan foto di belakangnya.
    val topBarBackdrop = rememberLayerBackdrop()

    val gridState = rememberLazyGridState()
    val items = viewModel.media.collectAsLazyPagingItems()

    val topBarTitle by remember(items) {
        derivedStateOf {
            val isAtTop = gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
            if (isAtTop || items.itemCount == 0) {
                DefaultTitle
            } else {
                items.peek(gridState.firstVisibleItemIndex)
                    ?.dateAddedEpochSeconds
                    ?.let(::formatTitleDate)
                    ?: DefaultTitle
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
        bottom = safeDrawingPadding.calculateBottomPadding() + FloatingTabBarHeight,
        start = safeDrawingPadding.calculateStartPadding(layoutDirection),
        end = safeDrawingPadding.calculateEndPadding(layoutDirection),
    )

    val previewItem by viewModel.previewItem.collectAsState()
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }

    // Launcher untuk konfirmasi hapus scoped-storage (dialog sistem, API 30+).
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            items.refresh()
        }
        viewModel.dismissPreview()
        pendingDeleteItem = null
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
            pendingDeleteItem = null
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
                        )
                    }
                },
                actions = {
                    SearchGlassButton(
                        onClick = onOpenSearch,
                        backdrop = topBarBackdrop,
                        liquidGlassSupported = liquidGlassSupported,
                    )
                    Spacer(Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { sourceModifier ->
        val gridBackdropModifier = if (liquidGlassSupported) {
            Modifier.fillMaxSize().layerBackdrop(topBarBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        MediaPermissionGate(modifier = gridBackdropModifier) {
            GalleryPagingContent(
                items = items,
                gridState = gridState,
                contentPadding = gridContentPadding,
                sourceModifier = sourceModifier,
                gridColumns = gridColumns,
                onMediaClick = { id, index -> onMediaClick(id, index, sortOrder) },
                onLongPress = { viewModel.showPreview(it) },
            )
        }
    }
        } // tutup Box background (blur)

        previewItem?.let { preview ->
            PhotoPreviewOverlay(
                item = preview,
                backdrop = topBarBackdrop,
                liquidGlassSupported = liquidGlassSupported,
                onDismiss = { viewModel.dismissPreview() },
                onDeleteClick = { pendingDeleteItem = preview },
            )
        }

        pendingDeleteItem?.let { pending ->
            DeleteConfirmDialog(
                onConfirm = {
                    viewModel.deletePhoto(pending.uri)
                    pendingDeleteItem = null
                    // previewItem ditutup setelah hasil delete diterima.
                },
                onDismiss = { pendingDeleteItem = null },
            )
        }
    } // tutup Box root
}

@Composable
private fun GalleryPagingContent(
    items: LazyPagingItems<MediaItem>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    sourceModifier: Modifier,
    gridColumns: Int,
    onMediaClick: (Long, Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
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
                        ?: "Something went wrong while loading your gallery.",
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
            onMediaClick = onMediaClick,
            onLongPress = onLongPress,
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
    onMediaClick: (Long, Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
) {
    GalleryPullToRefresh(
        items = items,
        contentPadding = contentPadding,
        sourceModifier = sourceModifier,
    ) { contentOffset ->
        val context = LocalContext.current
        LazyVerticalGrid(
            state = gridState,
            flingBehavior = rememberSensitiveFlingBehavior(),
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .then(contentOffset),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType { it.type },
            ) { index ->
                val item = items[index] ?: return@items
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedPhotoElement(key = "photo-${item.id}")
                        .combinedClickable(
                            onClick = { onMediaClick(item.id, index) },
                            onLongClick = { onLongPress(item) },
                        ),
                ) {
                    AsyncImage(
                        model = remember(item.uri, item.type) {
                            ImageRequest.Builder(context)
                                .data(item.uri)
                                .crossfade(true)
                                .apply {
                                    // Ambil frame di tengah video; frame detik 0 sering hitam.
                                    if (item.type == MediaType.VIDEO) {
                                        videoFramePercent(0.5)
                                    }
                                }
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
                }
            }
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
                    imageVector = PhosphorIcons.Regular.ImageSquare,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No photos yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Photos & videos on your device will appear here.",
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
                    imageVector = PhosphorIcons.Regular.WarningCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Couldn’t load",
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
                    Text("Try again")
                }
            }
        }
    }
}

/**
 * Tombol Search di top bar dengan efek liquid glass (Kyant drawBackdrop) yang
 * membiaskan foto grid di belakangnya. API 33+; di bawah itu fallback ke
 * Surface tonal semi-transparan (frosted).
 */
@Composable
private fun SearchGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    liquidGlassSupported: Boolean,
) {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.22f)
    if (liquidGlassSupported) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(12.dp.toPx(), 16.dp.toPx())
                    },
                    onDrawSurface = { drawRect(glassTint) },
                )
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = "Search photos"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.MagnifyingGlass,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(44.dp)
                .semantics { this.contentDescription = "Search photos" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PhosphorIcons.Regular.MagnifyingGlass,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
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
    liquidGlassSupported: Boolean,
    onDismiss: () -> Unit,
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
                    liquidGlassSupported = liquidGlassSupported,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }
}

/**
 * Menu konteks 1-foto dengan latar LIQUID GLASS (Kyant drawBackdrop; fallback
 * frosted untuk API < 33). Untuk sementara hanya berisi 1 aksi: Delete
 * (teks + ikon merah).
 */
@Composable
private fun PhotoContextMenu(
    backdrop: Backdrop,
    liquidGlassSupported: Boolean,
    onDeleteClick: () -> Unit,
) {
    // Pill shape supaya konsisten dgn lens Capsule() di drawBackdrop.
    val menuShape = RoundedCornerShape(percent = 50)
    val deleteColor = Color(0xFFFF453A) // merah destructive gaya iOS

    val glassModifier = if (liquidGlassSupported) {
        Modifier
            .clip(menuShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(12.dp.toPx(), 16.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.18f)) },
            )
    } else {
        Modifier
            .clip(menuShape)
            .background(Color.Black.copy(alpha = 0.6f))
    }

    Row(
        modifier = glassModifier
            .clickable { onDeleteClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = PhosphorIcons.Regular.Trash,
            contentDescription = null,
            tint = deleteColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Delete",
            color = deleteColor,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Dialog konfirmasi sebelum menghapus — di-render IN-COMPOSITION (bukan window
 * Dialog terpisah) supaya kartunya duduk di ATAS galeri yang SUDAH di-blur oleh
 * preview. Kombinasi kartu semi-transparan + background blur = kesan FROSTED /
 * liquid glass tanpa perlu menembus window lain.
 *
 * Tombol Delete merah; setelah confirm, hapus asli lewat scoped storage
 * (VM -> IntentSender). Tap area gelap / back = batal.
 */
@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deleteColor = Color(0xFFFF453A)

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(40.dp)
                .clip(RoundedCornerShape(28.dp))
                // Frosted glass: translucent surface di atas galeri yang sudah
                // di-blur oleh overlay preview -> tembus pandang seperti kaca.
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                // Konsumsi tap di kartu supaya tak ikut menutup dialog.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* no-op */ }
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.Trash,
                contentDescription = null,
                tint = deleteColor,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Delete photo?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This item will be permanently deleted from your " +
                    "device. This action can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Delete", color = deleteColor)
                }
            }
        }
    }
}
