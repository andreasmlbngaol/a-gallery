package id.andreasmbngaol.agallery.presentation.albums

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import id.andreasmbngaol.agallery.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.PushPin
import com.adamglin.phosphoricons.bold.PushPinSlash
import com.adamglin.phosphoricons.bold.Trash
import id.andreasmbngaol.agallery.domain.model.LOCKED_PIN_ALBUM_KEYS
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.core.ui.EdgeEffectTopBarScaffold
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel

/**
 * Tab Albums. Grid 3 kolom dgn dua section: "Pinned" (album cerdas + folder
 * yang di-pin user) dan "More" (sisa folder, diurut nama).
 *
 * ## Interaksi hold (meniru long-press di GalleryGridScreen)
 *
 * - Tap tile -> buka layar detail (grid ter-scope).
 * - Long-press tile (tanpa geser) -> masuk mode PREVIEW: kartu "maju"
 *   (scale-up spring) di tengah layar, background grid di-blur, muncul SATU
 *   tombol Pin/Unpin. Nav bar bawah otomatis tertutup lewat callback
 *   [onPreviewActiveChange] (persis seperti photo-hold di gallery).
 * - Long-press tile pinned + GESER -> masuk mode REORDER in-place: kartu
 *   yang ditahan mengikuti jari, kartu lain di section Pinned reflow. Lepas
 *   jari -> commit urutan baru ke VM. Tidak ada layar terpisah lagi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    edgeEffectMode: EdgeEffectMode?,
    componentStyle: ComponentStyle,
    onAlbumClick: (albumKey: String, name: String) -> Unit,
    onOpenTrash: () -> Unit = {},
    onPreviewActiveChange: (Boolean) -> Unit = {},
) {
    val viewModel: AlbumsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    // #8 (scroll-position hilang saat back dari AlbumDetail): pertahankan
    // konten terakhir. Ketika kembali, flow bisa sesaat re-emit Loading -> grid
    // sempat KOSONG -> LazyGrid kehilangan anchor & reset ke atas. Dgn menahan
    // Content terakhir saat state sedang Loading, grid tak pernah kosong
    // mendadak sehingga posisi scroll ter-restore dgn benar.
    var lastContent by remember { mutableStateOf<AlbumsUiState.Content?>(null) }
    LaunchedEffect(state) { (state as? AlbumsUiState.Content)?.let { lastContent = it } }
    val effectiveState: AlbumsUiState =
        if (state is AlbumsUiState.Loading) lastContent ?: state else state

    // ==== State long-press / drag ==================================
    // holdingAlbum: kartu yang lagi ditahan (preview overlay muncul saat
    // dragging == null).
    var holdingAlbum by remember { mutableStateOf<Pair<Album, Boolean>?>(null) }
    // draggingKey != null -> mode reorder in-place aktif untuk pinned itu.
    var draggingKey by remember { mutableStateOf<String?>(null) }

    val previewOverlayActive = holdingAlbum != null && draggingKey == null

    // Kabar ke HomeTabsScreen -> nav bar tertutup selama hold preview aktif.
    // PENTING: panggil callback LANGSUNG di dalam setter agar tidak bergantung
    // pada LaunchedEffect/derivedStateOf timing (yg pernah bikin nav bar
    // telat tertutup / tidak tertutup sama sekali di sebagian device).
    val onPreviewActive = rememberUpdatedState(onPreviewActiveChange)
    val setHolding: (Pair<Album, Boolean>?) -> Unit = { value ->
        holdingAlbum = value
        onPreviewActive.value(value != null && draggingKey == null)
    }
    val setDragging: (String?) -> Unit = { value ->
        draggingKey = value
        onPreviewActive.value(holdingAlbum != null && value == null)
    }
    DisposableEffect(Unit) {
        onDispose { onPreviewActive.value(false) }
    }

    // Local mutable copy dari list pinned untuk reorder real-time.
    // Selalu resync tiap pinned dari VM berubah (kecuali sedang di-drag).
    val pinnedRaw = (effectiveState as? AlbumsUiState.Content)?.pinned.orEmpty()
    // PENTING (fix scroll turun ke bawah saat back dari AlbumDetail): inisialisasi
    // list SUDAH terisi pinnedRaw pada frame pertama. Kalau di-init kosong lalu
    // baru diisi lewat LaunchedEffect (1 frame telat), frame pertama grid punya
    // item lebih sedikit -> indeks scroll yg di-restore mendarat di bawah (Trash).
    val pinnedList: SnapshotStateList<Album> = remember { pinnedRaw.toMutableStateList() }
    LaunchedEffect(pinnedRaw, draggingKey) {
        if (draggingKey == null) {
            pinnedList.clear()
            pinnedList.addAll(pinnedRaw)
        }
    }

    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val gridState = rememberLazyGridState()

    // Backdrop untuk styling liquid-glass tombol Pin/Unpin di overlay.
    // Grid (yg sudah di-blur) menjadi sumber sample supaya pill mengikuti
    // ComponentStyle (SOLID / FROSTED / GLASS) konsisten dgn tombol lain.
    val overlayBackdrop = rememberLayerBackdrop()

    EdgeEffectTopBarScaffold(
        title = stringResource(R.string.tab_albums),
        edgeEffectMode = edgeEffectMode,
    ) { contentModifier ->
        Box(modifier = contentModifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = safeDrawing.calculateStartPadding(layoutDirection) + 12.dp,
                    end = safeDrawing.calculateEndPadding(layoutDirection) + 12.dp,
                    top = safeDrawing.calculateTopPadding() + ScreenTopBarHeight + 8.dp,
                    bottom = safeDrawing.calculateBottomPadding() + 12.dp + FloatingTabBarHeight,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // layerBackdrop membuat grid ini jadi sumber sample untuk
                    // drawBackdrop di pill overlay -- pill bakal "membiaskan"
                    // grid yg sudah di-blur sesuai ComponentStyle.
                    .layerBackdrop(overlayBackdrop)
                    // Grid di-blur saat preview overlay aktif (hold tanpa drag).
                    .then(if (previewOverlayActive) Modifier.blur(20.dp) else Modifier)
                    .graphicsLayer { alpha = if (previewOverlayActive) 0.9f else 1f },
            ) {
                when (val current = effectiveState) {
                    is AlbumsUiState.Content -> {
                        // ---------- Pinned ----------
                        if (pinnedList.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(stringResource(R.string.albums_pinned))
                            }
                            items(
                                items = pinnedList,
                                key = { "pin:${it.key}" },
                            ) { album ->
                                val isDragging = album.key == draggingKey
                                val isLocked = album.key in LOCKED_PIN_ALBUM_KEYS
                                PinnedAlbumCard(
                                    album = album,
                                    isDragging = isDragging,
                                    gridState = gridState,
                                    pinnedList = pinnedList,
                                    onClick = { onAlbumClick(album.key, album.name) },
                                    onLongPressStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Untuk pinned yg locked (Recent/Videos/Favorites),
                                        // hold tetap menampilkan preview -- HANYA tombol
                                        // Unpin yg disembunyikan (lihat HoldPreviewOverlay).
                                        setHolding(album to true)
                                    },
                                    onDragEnter = {
                                        // Begitu jari mulai bergeser: preview overlay
                                        // ditutup, mode reorder in-place aktif.
                                        setHolding(null)
                                        setDragging(album.key)
                                    },
                                    onDragCommit = {
                                        val order = pinnedList.map { it.key }
                                        setDragging(null)
                                        viewModel.setOrder(order)
                                    },
                                    onDragCancel = { setDragging(null) },
                                )
                            }
                        }
                        // ---------- More ----------
                        if (current.more.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(stringResource(R.string.action_more))
                            }
                            items(
                                items = current.more,
                                key = { "more:${it.key}" },
                            ) { album ->
                                MoreAlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album.key, album.name) },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        setHolding(album to false)
                                    },
                                )
                            }
                        }
                        // ---------- Trash (row full-width, bukan kartu) ----------
                        current.trash?.let { trashAlbum ->
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "trash-row",
                            ) {
                                TrashRow(
                                    countText = if (trashAlbum.photoCount == 0) stringResource(R.string.albums_trash_empty)
                                        else pluralStringResource(R.plurals.item_count, trashAlbum.photoCount, trashAlbum.photoCount),
                                    onClick = onOpenTrash,
                                )
                            }
                        }
                    }
                    is AlbumsUiState.Empty -> {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
                    }
                    is AlbumsUiState.Loading -> Unit
                }
            }

            // Overlay preview (kartu-membesar + tombol Pin/Unpin) muncul HANYA
            // saat hold tanpa drag. Jangan di-render saat reorder.
            holdingAlbum?.takeIf { draggingKey == null }?.let { (album, isPinned) ->
                HoldPreviewOverlay(
                    album = album,
                    isPinned = isPinned,
                    isLocked = album.key in LOCKED_PIN_ALBUM_KEYS,
                    style = componentStyle,
                    backdrop = overlayBackdrop,
                    onDismiss = { setHolding(null) },
                    onPin = {
                        viewModel.pin(album.key)
                        setHolding(null)
                    },
                    onUnpin = {
                        viewModel.unpin(album.key)
                        setHolding(null)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Cards
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        AlbumCardBody(album = album)
    }
}

/**
 * Kartu di section "Pinned". Selain tap & long-press biasa, kartu ini juga
 * mendengarkan gesture DRAG-setelah-long-press ([detectDragGesturesAfterLongPress])
 * -- kalau user menahan lalu MENGGESER, kartu ini pindah tempat langsung di
 * grid tanpa overlay reorder terpisah.
 */
@Composable
private fun PinnedAlbumCard(
    album: Album,
    isDragging: Boolean,
    gridState: LazyGridState,
    pinnedList: SnapshotStateList<Album>,
    onClick: () -> Unit,
    onLongPressStart: () -> Unit,
    onDragEnter: () -> Unit,
    onDragCommit: () -> Unit,
    onDragCancel: () -> Unit,
) {
    // Offset kumulatif jari selama drag (px). Reset tiap drag berakhir.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // rememberUpdatedState supaya lambda di dalam pointerInput (long-lived
    // coroutine) selalu memanggil callback terbaru meski parent recompose.
    val onClickLatest by rememberUpdatedState(onClick)
    val onLongPressStartLatest by rememberUpdatedState(onLongPressStart)
    val onDragEnterLatest by rememberUpdatedState(onDragEnter)
    val onDragCommitLatest by rememberUpdatedState(onDragCommit)
    val onDragCancelLatest by rememberUpdatedState(onDragCancel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    // Scale sengaja kecil (1.04) + kartu di-scale dari tengah.
                    // Dikombinasikan dgn padding horizontal pd label (lihat
                    // AlbumCardBody) supaya teks nama & jumlah item TIDAK
                    // terpotong di kiri/kanan saat kartu terangkat.
                    scaleX = 1.04f
                    scaleY = 1.04f
                    // clip=false: layer tidak memotong konten yg melewati batas
                    // (bayangan & tepi kartu boleh menonjol saat diangkat).
                    clip = false
                    shadowElevation = 16f
                }
            }
            // SATU pointer input handler yang menangani tap, long-press-diam,
            // dan long-press-lalu-geser. Menggabungkan semuanya di sini
            // mencegah konflik saling-menelan-event antara combinedClickable
            // (long-click) dan detectDragGesturesAfterLongPress -- itulah yg
            // bikin long-press pinned card sebelumnya gagal menampilkan
            // overlay preview.
            .pointerInput(album.key, pinnedList.size) {
                val slop = viewConfiguration.touchSlop
                val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val outcome = awaitLongPressOrTap(down.id, longPressTimeoutMs, slop)
                    when (outcome) {
                        LongPressOutcome.Tap -> {
                            onClickLatest()
                            return@awaitEachGesture
                        }
                        LongPressOutcome.Cancel -> return@awaitEachGesture
                        LongPressOutcome.LongPress -> Unit
                    }
                    // Long-press terjadi -> munculkan preview overlay.
                    onLongPressStartLatest()
                    // Tunggu: user angkat (preview stay open) atau user geser
                    // (start reorder in-place).
                    var totalMove = Offset.Zero
                    var dragActive = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        if (!dragActive) {
                            totalMove += delta
                            if (totalMove.getDistance() > slop) {
                                dragActive = true
                                dragOffset = Offset.Zero
                                onDragEnterLatest()
                            }
                        }
                        if (dragActive) {
                            change.consume()
                            dragOffset += delta
                            // Reorder in-place.
                            val myIndex = pinnedList.indexOfFirst { it.key == album.key }
                            if (myIndex < 0) continue
                            val myItemInfo = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == "pin:${album.key}" } ?: continue
                            val centerX = myItemInfo.offset.x + myItemInfo.size.width / 2 + dragOffset.x.toInt()
                            val centerY = myItemInfo.offset.y + myItemInfo.size.height / 2 + dragOffset.y.toInt()
                            val targetItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                val key = info.key as? String ?: return@firstOrNull false
                                if (!key.startsWith("pin:") || key == "pin:${album.key}") return@firstOrNull false
                                val ox = info.offset.x
                                val oy = info.offset.y
                                centerX in ox..(ox + info.size.width) &&
                                    centerY in oy..(oy + info.size.height)
                            }
                            val targetKey = (targetItem?.key as? String)?.removePrefix("pin:")
                            val targetIndex = pinnedList.indexOfFirst { it.key == targetKey }
                            if (targetItem != null && targetIndex >= 0 && targetIndex != myIndex) {
                                // Kompensasi HARUS pakai targetItem.offset SEBELUM
                                // mutate pinnedList -- LazyGrid belum re-layout
                                // di frame yg sama. newDrag = (rumahLama - rumahBaru) + oldDrag.
                                val newHome = targetItem.offset
                                val oldHome = myItemInfo.offset
                                dragOffset = Offset(
                                    x = (oldHome.x - newHome.x) + dragOffset.x,
                                    y = (oldHome.y - newHome.y) + dragOffset.y,
                                )
                                val moved = pinnedList.removeAt(myIndex)
                                pinnedList.add(targetIndex, moved)
                            }
                        }
                    }
                    // Pointer up.
                    if (dragActive) {
                        dragOffset = Offset.Zero
                        onDragCommitLatest()
                    }
                    // Kalau bukan drag: long-press-diam, overlay stay open;
                    // di-dismiss lewat tap luar / tombol aksi pada overlay.
                }
            },
    ) {
        AlbumCardBody(album = album)
    }
}

/** Hasil deteksi long-press vs tap vs cancel di [awaitLongPressOrTap]. */
private enum class LongPressOutcome { LongPress, Tap, Cancel }

/**
 * Tunggu sampai salah satu terjadi:
 *  - Pointer diam >= [timeoutMs] -> [LongPressOutcome.LongPress].
 *  - Pointer up dalam batas slop -> [LongPressOutcome.Tap].
 *  - Pointer bergerak > slop sebelum timeout -> [LongPressOutcome.Cancel].
 */
private suspend fun AwaitPointerEventScope.awaitLongPressOrTap(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    timeoutMs: Long,
    slop: Float,
): LongPressOutcome {
    var totalMove = Offset.Zero
    val start = System.currentTimeMillis()
    while (true) {
        val remaining = timeoutMs - (System.currentTimeMillis() - start)
        if (remaining <= 0) return LongPressOutcome.LongPress
        val event = try {
            withTimeoutOrNull(remaining) { awaitPointerEvent() }
        } catch (t: Throwable) {
            null
        } ?: return LongPressOutcome.LongPress
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return LongPressOutcome.Cancel
        if (!change.pressed) return LongPressOutcome.Tap
        totalMove += change.positionChange()
        if (totalMove.getDistance() > slop) return LongPressOutcome.Cancel
    }
}

@Composable
private fun AlbumCardBody(album: Album) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .sharedPhotoElement(key = "albumcover-${album.key}")
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (album.coverUri != null) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    // padding horizontal kecil -> memberi "ruang napas" supaya saat kartu
    // pinned diangkat (di-scale) teks tidak menyentuh tepi sel & terpotong.
    Text(
        text = album.name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Text(
        text = albumSubtitle(album),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Format 2-baris dipakai saat album punya foto DAN video sekaligus,
        // biar tidak terpotong di grid 3-kolom yg lebar kartunya sempit.
        maxLines = 2,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Subtitle album: jumlah foto & video.
 *
 * Grid 3-kolom membuat lebar kartu sempit, sehingga format satu-baris
 * lama ("5 photos, 2 videos") sering terpotong. Solusi: kalau kedua tipe
 * ada, tampilkan DUA BARIS. Dipadu `maxLines = 2` pada Text pemakainya
 * supaya seluruh info muat tanpa ellipsis.
 */
@Composable
private fun albumSubtitle(album: Album): String {
    val p = album.photoCount
    val v = album.videoCount
    return when {
        p > 0 && v > 0 -> stringResource(R.string.album_photos_videos, p, v)
        p > 0 -> stringResource(R.string.album_photos_only, p)
        v > 0 -> stringResource(R.string.album_videos_only, v)
        else -> stringResource(R.string.album_no_photos)
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.albums_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------
//  Hold-preview overlay (kartu-membesar + tombol Pin/Unpin)
// ---------------------------------------------------------------------

/**
 * Overlay saat album ditahan (tanpa geser). Meniru pola long-press di
 * [id.andreasmbngaol.agallery.presentation.gallery.GalleryGridScreen]:
 *  - Background gelap 45% + tap-outside dismiss.
 *  - Kartu "maju" (spring dari scale 0.85 -> 1.0) di tengah.
 *  - Satu tombol pill Pin/Unpin di bawah kartu.
 */
@Composable
private fun HoldPreviewOverlay(
    album: Album,
    isPinned: Boolean,
    isLocked: Boolean,
    style: ComponentStyle,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Animasi "membesar" persis seperti PhotoPreviewOverlay di Gallery.
        val enterScale = remember { Animatable(0.85f) }
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = enterScale.value
                    scaleY = enterScale.value
                }
                // Konsumsi tap di area konten supaya tak menutup overlay.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* no-op */ },
        ) {
            // Kartu cover -- 60% lebar layar, jaga aspek 1:1.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = albumSubtitle(album),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            // Satu tombol pill: Pin atau Unpin -- styling MENGIKUTI
            // ComponentStyle (SOLID / FROSTED / GLASS) sama seperti
            // StyledCircleBackButton & PhotoContextMenu di Gallery:
            //  - GLASS  : drawBackdrop + blur + lens (liquid glass)
            //  - FROSTED: drawBackdrop tanpa blur/lens (veil haze)
            //  - SOLID / < API 33: fallback background hitam semi-transparan
            val pillShape = RoundedCornerShape(percent = 50)
            val pillModifier = if (style.drawsBackdrop()) {
                Modifier
                    .clip(pillShape)
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
                                Color.White.copy(
                                    alpha = if (style == ComponentStyle.FROSTED) 0.3f else 0.18f,
                                ),
                            )
                        },
                    )
            } else {
                Modifier
                    .clip(pillShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            }

            // Pill Pin/Unpin -- disembunyikan untuk album locked (Recent /
            // Videos / Favorites) supaya user tidak bisa unpin. Preview
            // visual + kartu maju tetap muncul (hint bahwa hold berhasil).
            if (!(isPinned && isLocked)) {
                Row(
                    modifier = pillModifier
                        .clickable { if (isPinned) onUnpin() else onPin() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (isPinned) PhosphorIcons.Bold.PushPinSlash else PhosphorIcons.Bold.PushPin,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (isPinned) stringResource(R.string.action_unpin) else stringResource(R.string.action_pin_to_top),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Baris Trash di paling bawah tab Albums. SENGAJA tidak berbentuk kartu
 * grid supaya beda dari album biasa -- ini bukan album, ini bucket khusus
 * yg juga tidak boleh di-pin / di-reorder.
 *
 * Visual: divider tipis di atas + row full-width dgn ikon lingkaran, judul,
 * jumlah item, dan chevron kanan. Klik -> [onClick] (buka layar Trash).
 */
@Composable
private fun TrashRow(
    countText: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = PhosphorIcons.Bold.Trash,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(R.string.trash_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = true),
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Icon(
                imageVector = PhosphorIcons.Bold.CaretRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// SnapshotStateList<Album> factory (typed helper agar tak perlu inline
// annotation di call site).
private fun mutableStateListOfAlbums(): SnapshotStateList<Album> =
    emptyList<Album>().toMutableStateList()
