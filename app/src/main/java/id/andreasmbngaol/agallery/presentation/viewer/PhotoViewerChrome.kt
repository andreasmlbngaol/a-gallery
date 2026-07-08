package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.adamglin.phosphoricons.bold.Sparkle
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

private val GlassBlurRadius = 4.dp
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
private const val GlassTintAlpha = 0.3f
private const val FrostedHazeAlpha = 0.4f
private const val FrostedFallbackAlpha = 0.55f
private const val SolidFallbackAlpha = 0.95f

/** Hold duration on Trash before triggering permanent delete. */
private const val HoldToDeleteMs = 700

private val DangerRed = Color(0xFFFF453A)
private val FavoritePink = Color(0xFFFF375F)

private val HoldDeleteGradient = Brush.sweepGradient(
    listOf(
        Color(0xFFFF453A),
        Color(0xFFFF2D92),
        Color(0xFFAF52DE),
        Color(0xFFFF453A),
    ),
)

/**
 * A **liquid glass** container modifier (Kyant backdrop, API 33+ & setting ON).
 * If unsupported / glass OFF -> falls back to a frosted solid.
 *
 * [style] determines the render: GLASS = liquid glass (Kyant refraction),
 * FROSTED/SOLID = fallback fill (translucent / nearly opaque). Resolved in
 * [PhotoViewerScreen] the same way as the gallery nav bar.
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
 * A round liquid-glass button (Back, Info, More). The content is colored
 * `onSurface` by the caller for contrast in both light & dark.
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
 * A WIDE glass-style action button (SOLID/FROSTED/GLASS) for use outside an
 * island — e.g. the "Remove metadata" button in the detail sheet — to stay
 * consistent with the app theme. Capsule shape (CircleShape = a pill for wide
 * elements), content colored onSurface for contrast in both light & dark.
 */
@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    style: ComponentStyle,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .liquidGlass(style, backdrop)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

/**
 * A liquid-glass capsule "island" wrapping a row of buttons (e.g. Share ·
 * Delete · Favorite). The buttons inside are transparent; only the island has
 * glass (similar to the pill-track pattern in the gallery nav bar).
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
 * Viewer top bar: Back (left) + Info (right) as liquid-glass buttons
 * (depending on the setting). No more full gradient scrim — each button
 * stands on its own over the glass.
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
 * Bottom action bar for PHOTOS: two separate islands.
 * - Island A (capsule): Share · Delete(hold) · Favorite.
 * - Island B (separate): the More button (⋮).
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
    onAiClick: (() -> Unit)? = null,
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
        if (onAiClick != null) {
            GlassIconButton(
                onClick = onAiClick,
                contentDescription = stringResource(R.string.ai_sheet_title),
                style = style,
                backdrop = backdrop,
            ) {
                Icon(PhosphorIcons.Bold.Sparkle, contentDescription = null, tint = tint)
            }
        }
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
 * Action row for VIDEO: Share · Delete(hold) · Favorite · More, all combined
 * into ONE row without its own glass (mounted inside the combined video island
 * owned by [VideoPlayerContent], below the controls row).
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

/** Glass version of the More button (for the separate photo island). */
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
    var showSheet by remember { mutableStateOf(false) }
    GlassIconButton(
        onClick = { showSheet = true },
        contentDescription = stringResource(R.string.action_more),
        style = style,
        backdrop = backdrop,
    ) {
        Icon(PhosphorIcons.Bold.DotsThreeVertical, contentDescription = null, tint = tint)
    }
    if (showSheet) {
        MoreActionsSheet(
            onDismiss = { showSheet = false },
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

/** Plain version of the More button (for the video action row inside the combined island). */
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
    var showSheet by remember { mutableStateOf(false) }
    IconButton(onClick = { showSheet = true }) {
        Icon(PhosphorIcons.Bold.DotsThreeVertical, contentDescription = stringResource(R.string.action_more), tint = tint)
    }
    if (showSheet) {
        MoreActionsSheet(
            onDismiss = { showSheet = false },
            onRename = onRename,
            onSetAsCover = onSetAsCover,
            onOpenWith = onOpenWith,
            onCopy = onCopy,
            onMove = onMove,
            onDelete = onDelete,
        )
    }
}

/**
 * Bottom sheet of "More" actions for a photo/video, opened from the More button.
 * Replaces the old dropdown menu so it matches the AI sheet and keeps a
 * consistent, non-shifting UX. [onSetAs] and [onConvertFormat] are null for
 * videos (those rows are hidden).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onSetAs: (() -> Unit)? = null,
    onSetAsCover: () -> Unit = {},
    onConvertFormat: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            MoreActionRow(PhosphorIcons.Bold.PencilSimple, stringResource(R.string.action_rename)) {
                onDismiss(); onRename()
            }
            MoreActionRow(PhosphorIcons.Bold.Image, stringResource(R.string.action_set_as_cover)) {
                onDismiss(); onSetAsCover()
            }
            if (onSetAs != null) {
                MoreActionRow(PhosphorIcons.Bold.Image, stringResource(R.string.action_set_as_wallpaper)) {
                    onDismiss(); onSetAs()
                }
            }
            MoreActionRow(PhosphorIcons.Bold.ArrowSquareOut, stringResource(R.string.action_open_with)) {
                onDismiss(); onOpenWith()
            }
            MoreActionRow(PhosphorIcons.Bold.Copy, stringResource(R.string.action_copy_to_album)) {
                onDismiss(); onCopy()
            }
            MoreActionRow(PhosphorIcons.Bold.FolderSimple, stringResource(R.string.action_move_to_album)) {
                onDismiss(); onMove()
            }
            onConvertFormat?.let { onConvert ->
                MoreActionRow(PhosphorIcons.Bold.Image, stringResource(R.string.action_convert_format)) {
                    onDismiss(); onConvert()
                }
            }
            MoreActionRow(
                icon = PhosphorIcons.Fill.Trash,
                text = stringResource(R.string.action_delete),
                danger = true,
            ) {
                onDismiss(); onDelete()
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) DangerRed else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

/**
 * Trash button with a "hold to Delete" affordance:
 * - A normal tap -> move to Trash (now via an on-screen confirmation dialog).
 * - Held -> a colorful gradient circle (red/pink/purple) grows from the center
 *   filling the button (~700ms). When full -> LongPress haptic + [onHoldComplete]
 *   (permanent delete; confirmation deferred to the Android system dialog).
 *
 * A small vibration when the press starts signals that this button can be held.
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
    val dotIndex = initialName.lastIndexOf('.')
    val hasExt = dotIndex > 0
    val extension = if (hasExt) initialName.substring(dotIndex) else ""
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
 * MOVE TO TRASH confirmation. Added so items are not trashed immediately when the
 * button is accidentally tapped (this action is reversible, so the confirmation is light).
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

