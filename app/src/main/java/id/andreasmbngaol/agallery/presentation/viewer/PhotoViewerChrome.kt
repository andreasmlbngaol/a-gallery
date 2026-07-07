package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Trash
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.ArrowSquareOut
import com.adamglin.phosphoricons.bold.Copy
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.bold.FolderSimple
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.Image
import com.adamglin.phosphoricons.bold.Info
import com.adamglin.phosphoricons.bold.PencilSimple
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.Trash
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle

// ---- Tuning liquid glass (samain dgn floating nav bar di GalleryTabScaffold) ----
private val GlassBlurRadius = 4.dp
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
private const val GlassTintAlpha = 0.3f
// Veil "haze" FROSTED (drawBackdrop TANPA blur/lens) -> sedikit lebih pekat.
private const val FrostedHazeAlpha = 0.4f
// Fallback fill (API < 33): FROSTED translusen (masih berkesan kaca), SOLID hampir opaque.
private const val FrostedFallbackAlpha = 0.55f
private const val SolidFallbackAlpha = 0.95f

/** Durasi tahan Trash sampai memicu hapus permanen. */
private const val HoldToDeleteMs = 700

private val DangerRed = Color(0xFFFF453A)
private val FavoritePink = Color(0xFFFF375F)

// Gradient "playful" merah -> pink -> ungu untuk animasi tahan-hapus. Sweep biar
// warnanya muter warna-warni; balik ke merah di akhir supaya sambungannya mulus.
private val HoldDeleteGradient = Brush.sweepGradient(
    listOf(
        Color(0xFFFF453A), // red
        Color(0xFFFF2D92), // pink
        Color(0xFFAF52DE), // purple
        Color(0xFFFF453A), // balik merah
    ),
)

/**
 * Modifier container **liquid glass** (Kyant backdrop, API 33+ & setting ON).
 * Kalau tidak didukung / glass OFF -> fallback frosted solid.
 *
 * [style] menentukan render: GLASS = liquid glass (Kyant refraction),
 * FROSTED/SOLID = fallback fill (translusen / hampir opaque). Di-resolve di
 * [PhotoViewerScreen] sama seperti nav bar gallery.
 */
@Composable
private fun Modifier.liquidGlass(
    style: ComponentStyle,
    backdrop: Backdrop,
): Modifier {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) FrostedHazeAlpha else GlassTintAlpha,
    )
    val fallbackTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) FrostedFallbackAlpha else SolidFallbackAlpha,
    )
    return if (style.drawsBackdrop()) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                // GLASS = blur + lens. FROSTED = keduanya off -> vibrancy + veil
                // haze saja (kabut); bentuk tetap kaca tapi tanpa distorsi & blur.
                if (style.usesBlur()) {
                    blur(GlassBlurRadius.toPx())
                }
                if (style.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        clip(CircleShape).background(fallbackTint)
    }
}

/**
 * Tombol lingkaran liquid glass (Back, Info, More). Konten diberi warna
 * `onSurface` oleh pemanggil supaya kontras di light & dark.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    style: ComponentStyle,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .liquidGlass(style, backdrop)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Tombol aksi LEBAR bergaya kaca (SOLID/FROSTED/GLASS) untuk dipakai di luar
 * island — mis. tombol "Hapus metadata" di detail sheet — biar seragam dgn
 * tema app. Bentuk kapsul (CircleShape = pill utk elemen lebar), konten diberi
 * warna onSurface supaya kontras di light & dark.
 */
@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    style: ComponentStyle,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .liquidGlass(style, backdrop)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}

/**
 * "Island" kapsul liquid glass yang membungkus sebaris tombol (mis. Share ·
 * Delete · Favorite). Tombol di dalamnya transparan; hanya island yang berkaca
 * (mirip pola track pill di nav bar gallery).
 */
@Composable
fun GlassIsland(
    style: ComponentStyle,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .liquidGlass(style, backdrop)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * Top bar viewer: Back (kiri) + Info (kanan) sebagai tombol liquid glass
 * (tergantung setting). Tidak ada lagi gradient scrim penuh — tiap tombol
 * berdiri sendiri di atas kaca.
 */
@Composable
fun ViewerTopBar(
    onBack: () -> Unit,
    onInfo: () -> Unit,
    style: ComponentStyle,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.action_back),
            style = style,
            backdrop = backdrop,
        ) {
            Icon(PhosphorIcons.Bold.ArrowLeft, contentDescription = null, tint = tint)
        }
        GlassIconButton(
            onClick = onInfo,
            contentDescription = stringResource(R.string.action_details),
            style = style,
            backdrop = backdrop,
        ) {
            Icon(PhosphorIcons.Bold.Info, contentDescription = null, tint = tint)
        }
    }
}

/**
 * Action bar bawah untuk FOTO: dua island terpisah.
 * - Island A (kapsul): Share · Delete(tahan) · Favorite.
 * - Island B (terpisah): tombol More (⋮).
 */
@Composable
fun ViewerActionBar(
    isFavorite: Boolean,
    style: ComponentStyle,
    backdrop: Backdrop,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onTrashTap: () -> Unit,
    onHoldDelete: () -> Unit,
    onRename: () -> Unit,
    onSetAs: () -> Unit,
    onSetAsCover: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onConvertFormat: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Island A: Share · Delete(tahan) · Favorite (urutan sesuai permintaan).
        GlassIsland(style, backdrop) {
            IconButton(onClick = onShare) {
                Icon(PhosphorIcons.Bold.ShareNetwork, contentDescription = stringResource(R.string.action_share), tint = tint)
            }
            HoldToDeleteButton(onTap = onTrashTap, onHoldComplete = onHoldDelete, tint = tint)
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
                    contentDescription = if (isFavorite) stringResource(R.string.action_remove_from_favorites) else stringResource(R.string.action_add_to_favorites),
                    tint = if (isFavorite) FavoritePink else tint,
                )
            }
        }
        // Island B: More (terpisah).
        GlassMoreButton(
            style = style,
            backdrop = backdrop,
            tint = tint,
            onRename = onRename,
            onSetAs = onSetAs,
            onSetAsCover = onSetAsCover,
            onOpenWith = onOpenWith,
            onCopy = onCopy,
            onMove = onMove,
            onConvertFormat = onConvertFormat,
            onDelete = onDelete,
        )
    }
}

/**
 * Baris aksi untuk VIDEO: Share · Delete(tahan) · Favorite · More, semuanya
 * digabung dalam SATU baris tanpa kaca sendiri (dipasang di dalam island video
 * gabungan milik [VideoPlayerContent], di bawah baris kontrol).
 */
@Composable
fun ViewerVideoActionRow(
    isFavorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onTrashTap: () -> Unit,
    onHoldDelete: () -> Unit,
    onRename: () -> Unit,
    onSetAsCover: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShare) {
            Icon(PhosphorIcons.Bold.ShareNetwork, contentDescription = stringResource(R.string.action_share), tint = tint)
        }
        HoldToDeleteButton(onTap = onTrashTap, onHoldComplete = onHoldDelete, tint = tint)
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
                contentDescription = if (isFavorite) stringResource(R.string.action_remove_from_favorites) else stringResource(R.string.action_add_to_favorites),
                tint = if (isFavorite) FavoritePink else tint,
            )
        }
        PlainMoreButton(
            tint = tint,
            onRename = onRename,
            onSetAsCover = onSetAsCover,
            onOpenWith = onOpenWith,
            onCopy = onCopy,
            onMove = onMove,
            onDelete = onDelete,
        )
    }
}

/** Tombol More versi kaca (untuk island foto terpisah). */
@Composable
private fun GlassMoreButton(
    style: ComponentStyle,
    backdrop: Backdrop,
    tint: Color,
    onRename: () -> Unit,
    onSetAs: () -> Unit,
    onSetAsCover: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onConvertFormat: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(
            onClick = { expanded = true },
            contentDescription = stringResource(R.string.action_more),
            style = style,
            backdrop = backdrop,
        ) {
            Icon(PhosphorIcons.Bold.DotsThreeVertical, contentDescription = null, tint = tint)
        }
        ViewerMoreDropdown(
            expanded = expanded,
            onDismiss = { expanded = false },
            onRename = onRename,
            onSetAs = onSetAs,
            onSetAsCover = onSetAsCover,
            onOpenWith = onOpenWith,
            onCopy = onCopy,
            onMove = onMove,
            onConvertFormat = onConvertFormat,
            onDelete = onDelete,
        )
    }
}

/** Tombol More versi polos (untuk baris aksi video di dalam island gabungan). */
@Composable
private fun PlainMoreButton(
    tint: Color,
    onRename: () -> Unit,
    onSetAsCover: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(PhosphorIcons.Bold.DotsThreeVertical, contentDescription = stringResource(R.string.action_more), tint = tint)
        }
        // Video: TANPA "Set as wallpaper" (wallpaper hanya relevan utk gambar).
        ViewerMoreDropdown(
            expanded = expanded,
            onDismiss = { expanded = false },
            onRename = onRename,
            onSetAsCover = onSetAsCover,
            onOpenWith = onOpenWith,
            onCopy = onCopy,
            onMove = onMove,
            onDelete = onDelete,
            showSetAs = false,
        )
    }
}

@Composable
private fun ViewerMoreDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onSetAs: () -> Unit = {},
    onSetAsCover: () -> Unit = {},
    // "Ubah format" hanya untuk gambar; video menyembunyikannya (null).
    onConvertFormat: (() -> Unit)? = null,
    // "Set as wallpaper" hanya untuk gambar; video menyembunyikannya.
    showSetAs: Boolean = true,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            leadingIcon = { Icon(PhosphorIcons.Bold.PencilSimple, contentDescription = null) },
            onClick = {
                onDismiss()
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_set_as_cover)) },
            leadingIcon = { Icon(PhosphorIcons.Bold.Image, contentDescription = null) },
            onClick = {
                onDismiss()
                onSetAsCover()
            },
        )
        if (showSetAs) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_set_as_wallpaper)) },
                leadingIcon = { Icon(PhosphorIcons.Bold.Image, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onSetAs()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_open_with)) },
            leadingIcon = { Icon(PhosphorIcons.Bold.ArrowSquareOut, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpenWith()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_copy_to_album)) },
            leadingIcon = { Icon(PhosphorIcons.Bold.Copy, contentDescription = null) },
            onClick = {
                onDismiss()
                onCopy()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_move_to_album)) },
            leadingIcon = { Icon(PhosphorIcons.Bold.FolderSimple, contentDescription = null) },
            onClick = {
                onDismiss()
                onMove()
            },
        )
        onConvertFormat?.let { onConvert ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_convert_format)) },
                leadingIcon = { Icon(PhosphorIcons.Bold.Image, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onConvert()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = DangerRed) },
            leadingIcon = { Icon(PhosphorIcons.Fill.Trash, contentDescription = null, tint = DangerRed) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/**
 * Tombol Trash dengan afordans "tahan untuk Delete":
 * - Tap biasa -> pindah ke Trash (kini lewat dialog konfirmasi di layar).
 * - Ditahan -> lingkaran gradient warna-warni (merah/pink/ungu) tumbuh dari
 *   tengah mengisi tombol (~700ms). Penuh -> haptic LongPress + [onHoldComplete]
 *   (hapus permanen; konfirmasi diserahkan ke dialog sistem Android).
 *
 * Getaran kecil saat mulai menekan menandakan tombol ini bisa ditahan.
 */
@Composable
fun HoldToDeleteButton(
    onTap: () -> Unit,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val haptics = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    var triggered by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = if (holding) HoldToDeleteMs else 180),
        label = "hold-to-delete",
    )

    LaunchedEffect(holding, progress) {
        if (holding && progress >= 0.999f && !triggered) {
            triggered = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onHoldComplete()
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        triggered = false
                        holding = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val released = tryAwaitRelease()
                        holding = false
                        if (released && !triggered) onTap()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Lingkaran gradient warna-warni yg membesar mengisi tombol seiring tekan.
        if (progress > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = progress
                        scaleY = progress
                        alpha = 0.45f + 0.5f * progress
                    }
                    .clip(CircleShape)
                    .background(HoldDeleteGradient),
            )
        }
        Icon(
            imageVector = PhosphorIcons.Bold.Trash,
            contentDescription = stringResource(R.string.move_to_trash_hold_hint),
            tint = tint,
        )
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Pisahkan base + ekstensi: user hanya mengedit base, ekstensi asli SELALU
    // dipertahankan (ditampilkan sbg suffix read-only). Mencegah kasus nama jadi
    // "Renamed" tanpa ekstensi -> yg bikin hasil convert ikut aneh (Renamed.webp).
    val dotIndex = initialName.lastIndexOf('.')
    val hasExt = dotIndex > 0
    val extension = if (hasExt) initialName.substring(dotIndex) else "" // termasuk titik
    var text by remember { mutableStateOf(if (hasExt) initialName.substring(0, dotIndex) else initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.viewer_file_name)) },
                suffix = if (extension.isNotEmpty()) {
                    { Text(extension) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entered = text.trim()
                    // Re-append ekstensi asli (kecuali user sudah mengetiknya).
                    val finalName = if (
                        extension.isNotEmpty() && !entered.endsWith(extension, ignoreCase = true)
                    ) {
                        entered + extension
                    } else {
                        entered
                    }
                    onConfirm(finalName)
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Konfirmasi PINDAH KE TRASH. Ditambahkan supaya tidak langsung ke-trash saat
 * tombol tak sengaja kepencet (aksi ini reversible, jadi konfirmasinya ringan).
 */
@Composable
fun MoveToTrashConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PhosphorIcons.Bold.Trash, contentDescription = null) },
        title = { Text(stringResource(R.string.move_to_trash_title)) },
        text = { Text(stringResource(R.string.move_to_trash_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_move_to_trash)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

