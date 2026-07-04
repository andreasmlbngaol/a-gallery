package id.andreasmbngaol.agallery.presentation.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.Trash
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.TrashItem
import org.koin.androidx.compose.koinViewModel

/**
 * Layar app-level Trash (30-hari soft-delete).
 *
 * ## Visual
 * - TopBar ala AlbumDetail: tombol back bulat (tint onBackground) + judul.
 * - Konten berupa **grid 3-kolom** biasa (seperti galeri) dgn badge di pojok
 *   KANAN-ATAS tiap thumbnail yang menampilkan sisa hari sebelum auto-delete
 *   (mis. "12d") -- meniru badge durasi video tapi di kanan-atas.
 *
 * ## Interaksi
 * - **Hold (long-press)** satu item -> overlay dgn 2 aksi: Restore / Delete.
 * - **Action island** (pill mengambang bawah): tombol Select untuk masuk mode
 *   pilih. Di mode pilih: Select all / Deselect all, tap item utk pilih
 *   banyak, lalu muncul Restore all & Delete all saat ada yg terpilih.
 *
 * Auto-purge 30-hari (worker) belum di-scope; badge hanya indikator.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    edgeEffectMode: EdgeEffectMode?,
    onBack: () -> Unit,
) {
    val viewModel: TrashViewModel = koinViewModel()
    val items by viewModel.items.collectAsState()

    // --- Mode seleksi (multi-select) ---
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // --- Hold overlay (aksi cepat 1 item) ---
    var holdingItem by remember { mutableStateOf<TrashItem?>(null) }

    // --- Pending SAF delete (id yg menunggu konfirmasi sistem) ---
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Kalau daftar berubah, buang id terpilih yg sudah tak ada & keluar dari
    // mode seleksi bila trash jadi kosong.
    LaunchedEffect(items) {
        val present = items.map { it.id }.toHashSet()
        selectedIds.retainAll(present)
        if (items.isEmpty()) {
            selectionMode = false
            selectedIds.clear()
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

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggle(id: Long) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectionMode) "${selectedIds.size} selected" else "Trash",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        CircleIconButton(
                            icon = PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "Back",
                            onClick = { if (selectionMode) exitSelection() else onBack() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                        top = innerPadding.calculateTopPadding() + 2.dp,
                        // Ruang ekstra bawah supaya item terakhir tak tertutup
                        // action island yg mengambang.
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = items,
                        key = { it.id },
                    ) { item ->
                        TrashGridItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selectedIds,
                            onClick = {
                                if (selectionMode) toggle(item.id)
                            },
                            onLongPress = {
                                if (!selectionMode) holdingItem = item
                            },
                        )
                    }
                }
            }

            // ---------- Action island ----------
            if (items.isNotEmpty()) {
                ActionIsland(
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    allSelected = selectedIds.size == items.size && items.isNotEmpty(),
                    onEnterSelection = { selectionMode = true },
                    onToggleSelectAll = {
                        if (selectedIds.size == items.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(items.map { it.id })
                        }
                    },
                    onRestoreAll = {
                        viewModel.restoreMany(selectedIds.toList())
                        exitSelection()
                    },
                    onDeleteAll = {
                        val toDelete = items.filter { it.id in selectedIds }
                        pendingDeleteIds = toDelete.map { it.id }
                        viewModel.requestPermanentDeleteMany(toDelete)
                    },
                    onCancel = { exitSelection() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 20.dp),
                )
            }

            // ---------- Hold overlay (Restore / Delete 1 item) ----------
            holdingItem?.let { item ->
                HoldActionOverlay(
                    item = item,
                    onDismiss = { holdingItem = null },
                    onRestore = {
                        viewModel.restore(item.id)
                        holdingItem = null
                    },
                    onDelete = {
                        pendingDeleteIds = listOf(item.id)
                        viewModel.requestPermanentDelete(item)
                        holdingItem = null
                    },
                )
            }
        }
    }
}

/** Tombol bulat 40dp dgn tint onBackground -- terbaca di light & dark. */
@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(onBg.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = onBg,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashGridItem(
    item: TrashItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
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
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

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

/** Badge "Nd" (sisa hari sebelum auto-delete) -- gaya sama seperti VideoBadge. */
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
            .background(
                if (selected) primary else Color.Black.copy(alpha = 0.35f),
            )
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // Gambar centang manual (hindari dependency ikon check).
            Canvas(modifier = Modifier.size(14.dp)) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.16f
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.18f, h * 0.55f),
                    end = Offset(w * 0.42f, h * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.42f, h * 0.78f),
                    end = Offset(w * 0.82f, h * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Action island mengambang di bawah.
 * - Sebelum mode pilih: hanya tombol "Select".
 * - Saat mode pilih: Select all / Deselect all + (jika ada terpilih) Restore &
 *   Delete + Cancel.
 */
@Composable
private fun ActionIsland(
    selectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onEnterSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!selectionMode) {
            IslandPillButton(
                label = "Select",
                onClick = onEnterSelection,
            )
        } else {
            IslandPillButton(
                label = if (allSelected) "Deselect all" else "Select all",
                onClick = onToggleSelectAll,
            )
            if (selectedCount > 0) {
                IslandIconButton(
                    icon = PhosphorIcons.Regular.ArrowClockwise,
                    label = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onRestoreAll,
                )
                IslandIconButton(
                    icon = PhosphorIcons.Regular.Trash,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDeleteAll,
                )
            }
            IslandPillButton(
                label = "Cancel",
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun IslandPillButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IslandIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

/**
 * Overlay saat item di-hold: latar gelap + thumbnail besar + 2 pill aksi
 * (Restore / Delete). Tap area gelap utk menutup.
 */
@Composable
private fun HoldActionOverlay(
    item: TrashItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                DaysLeftBadge(
                    trashedAt = item.trashedAt,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HoldActionPill(
                    icon = PhosphorIcons.Regular.ArrowClockwise,
                    label = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onRestore,
                )
                HoldActionPill(
                    icon = PhosphorIcons.Regular.Trash,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HoldActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** Sisa hari sebelum auto-delete (30 hari sejak di-trash). Mis. "12d". */
private fun formatDaysLeft(trashedAtMs: Long): String {
    val diffMs = (System.currentTimeMillis() - trashedAtMs).coerceAtLeast(0L)
    val days = diffMs / 86_400_000L
    val daysLeft = (30L - days).coerceAtLeast(0L)
    return "${daysLeft}d"
}
