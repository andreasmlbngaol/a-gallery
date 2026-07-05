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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Info
import com.adamglin.phosphoricons.bold.Trash
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnail
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.core.permission.AllFilesAccess
import id.andreasmbngaol.agallery.core.ui.ConfirmDeleteDialog
import id.andreasmbngaol.agallery.domain.model.TrashItem
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

// Thumbnail px target -- samain dgn GalleryGridScreen supaya cache & kualitas seragam.
private const val GridThumbnailPx = 400

/**
 * Layar app-level Trash (30-hari soft-delete).
 *
 * ## Konsistensi styling (mengikuti GalleryGridScreen / PhotoViewer)
 * - **Edge effect** via [SystemBarScrim] sesuai setting (Off/Darken/Blurry).
 * - **Topbar**: tombol Back kaca ([GlassIconButton]) + judul, mengikuti
 *   `componentStyle` (Solid/Frosted/Glass).
 * - **Action island** bawah: kapsul liquid-glass (pola sama dgn island
 *   PhotoViewer) berisi tombol Icon + label kecil di bawahnya.
 * - **Grid**: 3 kolom, thumbnail di-crop **1:1** (`ContentScale.Crop`) +
 *   badge durasi utk video + badge sisa-hari auto-delete.
 *
 * ## Interaksi
 * - **Tap** item (mode normal) -> buka viewer in-screen (pager) dgn aksi
 *   TERBATAS: hanya **Restore** & **Permanently delete**.
 * - **Long-press** item -> masuk mode seleksi (langsung men-select item itu).
 * - **Action island** mode seleksi: Select all / Restore / Delete / Cancel.
 *
 * ## Auto-purge 30 hari
 * Auto-purge utama kini dijalankan di background oleh [TrashPurgeWorker]
 * (harian) bila app punya All-files access -> hapus permanen tanpa dialog.
 * Sebagai cadangan, saat layar dibuka [TrashViewModel.autoPurgeExpired] juga
 * mengumpulkan item kedaluwarsa. Tanpa All-files access, hapus tetap lewat SAF
 * delete-many (1 dialog konfirmasi sistem).
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

    // --- Mode seleksi (multi-select) ---
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // --- Viewer in-screen (index item yg sedang dibuka; null = tertutup) ---
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    // --- Pending SAF delete (id yg menunggu konfirmasi sistem) ---
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // --- Pending konfirmasi hapus in-app (dipakai saat All-files access ON,
    //     karena dialog konfirmasi sistem tak lagi muncul) ---
    var pendingConfirm by remember { mutableStateOf<List<TrashItem>?>(null) }

    LaunchedEffect(items) {
        val present = items.map { it.id }.toHashSet()
        selectedIds.retainAll(present)
        if (items.isEmpty()) {
            selectionMode = false
            selectedIds.clear()
            viewerIndex = null
        } else {
            // Jaga viewerIndex tetap valid saat item berkurang (mis. setelah restore).
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

    // Auto-purge item kedaluwarsa sekali saat layar dibuka.
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
            // ---------- Top bar (glass back + title) ----------
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
                    contentDescription = "Back",
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
                    text = if (selectionMode) "${selectedIds.size} selected" else "Trash",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ---------- Action island (bawah) ----------
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
                            // Dialog sistem tak muncul -> konfirmasi in-app dulu.
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
                        text = "Trash is empty",
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
                        // ScreenTopBarHeight (72) + status bar sudah ditangani; beri ruang atas.
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

    // ---------- Viewer in-screen (Restore / Permanently delete saja) ----------
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

    // ---------- Konfirmasi hapus permanen in-app ----------
    // Saat All-files access ON, dialog konfirmasi sistem TIDAK muncul, jadi kita
    // tampilkan konfirmasi sendiri sebagai jaring pengaman sebelum hapus permanen.
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

        // Badge durasi video (pojok KANAN-BAWAH) -- sama dgn GalleryGridScreen.
        if (item.isVideo) {
            VideoBadge(
                durationMs = item.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }

        // Badge sisa hari auto-delete (pojok KANAN-ATAS).
        DaysLeftBadge(
            trashedAt = item.trashedAt,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )

        // Indikator seleksi (pojok KIRI-ATAS) hanya saat mode pilih.
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

/** Badge durasi video (Play + m:ss) -- meniru VideoBadge GalleryGridScreen. */
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
            text = formatDaysLeft(trashedAt),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
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
        if (selected) CheckGlyph(color = Color.White, modifier = Modifier.size(14.dp))
    }
}

/* --------------------------------------------------------------------------
 * Action island (liquid glass)
 * ------------------------------------------------------------------------ */

/**
 * Modifier kapsul liquid-glass (pola identik dgn island PhotoViewer). GLASS =
 * blur + lens; FROSTED = vibrancy + veil; SOLID/API<33 = fallback fill.
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
                label = "Select",
                tint = tint,
                onClick = onEnterSelection,
            ) { SelectGlyph(color = tint, modifier = Modifier.size(24.dp)) }
        } else {
            IslandAction(
                label = if (allSelected) "Select none" else "Select all",
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
                    label = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onRestore,
                )
                IslandAction(
                    icon = PhosphorIcons.Bold.Trash,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
            IslandAction(
                label = "Cancel",
                tint = tint,
                onClick = onCancel,
            ) { CloseGlyph(color = tint, modifier = Modifier.size(24.dp)) }
        }
    }
}

/**
 * Satu aksi di island: HANYA ikon (tanpa teks) supaya seragam & clean.
 * [label] dipakai sbg contentDescription utk aksesibilitas. Bisa pakai [icon]
 * (ImageVector) atau slot [glyph] kustom (centang/silang/checks via Canvas).
 */
@Composable
private fun IslandAction(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    // glyph HARUS param terakhir supaya bisa dipakai sbg trailing lambda: IslandAction(...) { ... }
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
    // Berapa jauh media diangkat saat panel detail terbuka (~22% tinggi layar),
    // sama seperti PhotoViewer biasa supaya media tetap kelihatan di atas sheet.
    val configuration = LocalConfiguration.current
    val liftPx = with(density) { (configuration.screenHeightDp.dp * 0.22f).toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Background ikut Material color scheme: light -> agak putih, dark -> hitam.
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
            // Saat panel detail terbuka, media diangkat ke atas supaya tetap
            // kelihatan di atas sheet (perilaku sama dgn PhotoViewer biasa).
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
                                // Hanya tangani tarikan ke bawah (drag-to-dismiss).
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
                                    // Swipe ke bawah cukup jauh -> tutup viewer.
                                    dragOffsetY.value >= dismissThresholdPx -> onClose()
                                    // Swipe ke atas cukup jauh -> buka panel detail.
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
                        // Gabungkan aksi Trash ke dalam control island video (jadi SATU,
                        // tidak terpisah/tumpang tindih). Hanya Restore/Delete + Details.
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
            // Top bar: hanya tombol Back kaca.
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
                    contentDescription = "Back",
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

            // Bottom island utk FOTO saja: Restore / Delete / Details.
            // (Video pakai action island gabungan di dalam kontrol player.)
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
                        label = "Restore",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onRestore(current) },
                    )
                    IslandAction(
                        icon = PhosphorIcons.Bold.Trash,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { onDelete(current) },
                    )
                    IslandAction(
                        icon = PhosphorIcons.Bold.Info,
                        label = "Details",
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
 * Baris aksi utk VIDEO di Trash, dipasang sbg [VideoPlayerContent] actionsSlot
 * supaya control player + aksi jadi SATU island. Hanya 2 aksi utama
 * (Restore/Delete) + tombol Details (karena di bawah cuma 2 aksi).
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
            label = "Restore",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onRestore,
        )
        IslandAction(
            icon = PhosphorIcons.Bold.Trash,
            label = "Delete",
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
        IslandAction(
            icon = PhosphorIcons.Bold.Info,
            label = "Details",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onDetails,
        )
    }
}

/** Panel detail item Trash (dibuka lewat swipe ke atas / tombol Details). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashDetailsSheet(
    item: TrashItem,
    loadDetails: suspend (String) -> MediaDetails?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DetailRow("Type", if (item.isVideo) "Video" else "Photo")
            if (item.isVideo) {
                DetailRow("Duration", formatVideoDuration(item.durationMs))
            }
            val d = details
            if (d != null) {
                DetailRow("Size", formatFileSize(d.sizeBytes))
                DetailRow("Dimensions", "${d.width} x ${d.height}")
                if (d.relativePath.isNotBlank()) {
                    DetailRow("Folder", d.relativePath)
                }
            }
            DetailRow("Deleted", formatTrashedDate(item.trashedAt))
            DetailRow("Auto-delete in", formatDaysLeft(item.trashedAt))
        }
    }
}

/** Satu baris label + value pada panel detail. */
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

/** Silang manual (untuk Cancel). */
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

/** Ikon "Select" (masuk mode seleksi): kotak membulat + centang di dalam. */
@Composable
private fun SelectGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        val corner = w * 0.22f
        // Kotak membulat (outline).
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        // Centang di dalam.
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

/** Ikon "Select none": kotak membulat kosong (outline saja, tanpa isi). */
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

/** Durasi video m:ss / h:mm:ss (sama dgn GalleryGridScreen). */
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

/** Sisa hari sebelum auto-delete (30 hari sejak di-trash). Mis. "12d". */
private fun formatDaysLeft(trashedAtMs: Long): String {
    val diffMs = (System.currentTimeMillis() - trashedAtMs).coerceAtLeast(0L)
    val days = diffMs / 86_400_000L
    val daysLeft = (30L - days).coerceAtLeast(0L)
    return "${daysLeft}d"
}

/** Byte -> string ramah baca (B/KB/MB/GB/TB, basis 1024). */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown"
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

/** Epoch millis -> tanggal-waktu lokal ramah baca (kapan item di-trash). */
private fun formatTrashedDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
