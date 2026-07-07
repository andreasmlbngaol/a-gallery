package id.andreasmbngaol.agallery.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import id.andreasmbngaol.agallery.R
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.SortAscending
import com.adamglin.phosphoricons.bold.SortDescending
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder

/** The four root tabs of AGallery's home screen. */
enum class GalleryTab { Settings, Gallery, Albums, Tools }

/**
 * Total footprint height of the floating bar measured from the bottom of the
 * content area, excluding `WindowInsets.navigationBars`.
 *
 * Screens inside the scaffold add this to their grid's bottom `contentPadding`
 * so the last item is not covered. It equals 12dp (bar top padding) + 52dp
 * (button height) + 12dp (bar bottom padding) = 76dp.
 */
val FloatingTabBarHeight = 76.dp

/** Diameter of the circular floating action buttons (Sort, Create album). */
private val FloatingButtonSize = 52.dp

/**
 * Fixed width of the tab pill. Deliberately narrower than all tabs combined so
 * the neighbours of the selected tab peek symmetrically on both sides.
 */
private val PillWidth = 176.dp

/** Gap between chips inside the pill. */
private val TabGap = 2.dp

/** Inset of the selected chip from each edge of its segment. */
private val SelectionInset = 4.dp

/**
 * Thin halo/outline so text stays readable over liquid glass even when the
 * content behind is a similar color. Text blur is in pixels, icon blur in dp.
 */
private const val TextHaloBlur = 4f
private val IconHaloBlur = 3.dp

/** Glass blur radius for the liquid-glass surfaces (Kyant backdrop, API 33+ only). */
private val GlassBlurRadius = 4.dp

/** Height and amount of the "liquid glass" edge refraction (`lens(height, amount)`). */
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp

/** Tint alpha over GLASS surfaces (pill track & buttons) to keep them translucent. */
private const val GlassTintAlpha = 0.22f

/** Tint alpha of the selected GLASS segment; denser, so the glass reads as more solid. */
private const val SelectedGlassTintAlpha = 0.55f

/**
 * Haze veil alpha for FROSTED over the backdrop (drawn without blur or lens).
 * Slightly denser than [GlassTintAlpha] because, without blur, the veil is what
 * conveys the frosted look.
 */
private const val FrostedHazeAlpha = 0.33f
private const val SelectedFrostedHazeAlpha = 0.6f

/**
 * Non-glass fill alpha for the FROSTED style (a static translucent frosted
 * pane). SOLID does not use alpha; it relies on tonal contrast
 * (`surfaceContainerLow` for the track, `surfaceContainerHighest` for the
 * selected chip), because alpha over an opaque same-colored track is nearly
 * invisible.
 */
private const val FrostedFallbackAlpha = 0.55f

/** Selected-segment alpha for the FROSTED style; denser than the frosted track. */
private const val SelectedFrostedAlpha = 0.8f

/**
 * Root screen scaffold: full-screen content plus a floating bottom bar holding
 * the tab pill [ Settings | Gallery | Albums | Tools ]. Action buttons appear
 * contextually — Sort on the Gallery tab, Create album ("+") on the Albums tab.
 *
 * ## Liquid glass
 *
 * The tab pill and the circular buttons use the **liquid glass** refraction
 * effect from the Kyant `backdrop` library, which requires `RuntimeShader`
 * (Android 13 / API 33+). Below that it falls back to a solid frosted
 * container. So the glass can refract the content behind it, [content] is
 * tagged as a `layerBackdrop` (the backdrop source) when the device supports
 * it.
 *
 * @param selectedTab the currently selected tab.
 * @param onSelectTab invoked when a tab is selected.
 * @param sortOrder the current gallery sort order, shown on the Sort button.
 * @param onToggleSort invoked when the Sort button is tapped.
 * @param modifier the modifier applied to the scaffold root.
 * @param barVisible whether the floating bar is shown; hidden during long-press
 *   preview so it cannot be used to switch tabs.
 * @param componentStyle the resolved surface style (SOLID / FROSTED / GLASS).
 * @param contentInteracting whether the content grid is currently being
 *   scrolled; a FROSTED bar freezes its backdrop capture to save GPU. No effect
 *   for GLASS (always live) or SOLID; kept for caller compatibility.
 * @param onNavBarDrag invoked with the raw horizontal drag delta on the bar, so
 *   the host can move the pager continuously.
 * @param onNavBarDragStart invoked when a bar drag begins, cancelling any settle.
 * @param onNavBarDragEnd invoked when a bar drag ends, so the host snaps to the
 *   nearest tab.
 * @param onCreateAlbum invoked when the Albums "+" button is tapped.
 * @param content the full-screen content, also the liquid-glass backdrop source.
 */
@Composable
fun GalleryTabScaffold(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    componentStyle: ComponentStyle = ComponentStyle.FROSTED,
    contentInteracting: Boolean = false,
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
    onCreateAlbum: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val captureBackdrop =
        componentStyle.drawsBackdrop() &&
            (componentStyle.usesLiveBackdrop() || !contentInteracting)
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        val contentModifier =
            if (captureBackdrop) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        Box(modifier = contentModifier) {
            content()
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingTabBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                sortOrder = sortOrder,
                onToggleSort = onToggleSort,
                backdrop = backdrop,
                componentStyle = componentStyle,
                onNavBarDrag = onNavBarDrag,
                onNavBarDragStart = onNavBarDragStart,
                onNavBarDragEnd = onNavBarDragEnd,
                onCreateAlbum = onCreateAlbum,
            )
        }
    }
}

/**
 * The floating bar row: a balancing spacer, the centered tab pill, and the
 * contextual action button (Sort on Gallery, "+" on Albums). The fixed-width,
 * always-centered pill keeps the layout stable when the action button appears
 * or disappears.
 *
 * @param selectedTab the currently selected tab.
 * @param onSelectTab invoked when a tab is selected.
 * @param sortOrder the current gallery sort order.
 * @param onToggleSort invoked when the Sort button is tapped.
 * @param backdrop the shared backdrop source for the glass surfaces.
 * @param componentStyle the resolved surface style.
 * @param modifier the modifier applied to the row.
 * @param onCreateAlbum invoked when the Albums "+" button is tapped.
 * @param onNavBarDrag invoked with the raw horizontal drag delta.
 * @param onNavBarDragStart invoked when a bar drag begins.
 * @param onNavBarDragEnd invoked when a bar drag ends.
 */
@Composable
private fun FloatingTabBar(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    modifier: Modifier = Modifier,
    onCreateAlbum: () -> Unit = {},
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
) {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (componentStyle == ComponentStyle.FROSTED) FrostedHazeAlpha else GlassTintAlpha,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedTab == GalleryTab.Gallery || selectedTab == GalleryTab.Albums) {
            Spacer(Modifier.size(FloatingButtonSize))
        }

        TabSwitcher(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            backdrop = backdrop,
            componentStyle = componentStyle,
            glassTint = glassTint,
            onNavBarDrag = onNavBarDrag,
            onNavBarDragStart = onNavBarDragStart,
            onNavBarDragEnd = onNavBarDragEnd,
        )

        if (selectedTab == GalleryTab.Gallery) {
            CircularFloatingButton(
                onClick = onToggleSort,
                contentDescription = stringResource(R.string.action_change_sort_order),
                backdrop = backdrop,
                componentStyle = componentStyle,
                glassTint = glassTint,
            ) {
                AnimatedContent(
                    targetState = sortOrder,
                    transitionSpec = {
                        val goingToAsc = targetState == GallerySortOrder.DateAsc
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(220)) +
                                slideInVertically(tween(260)) { h ->
                                    if (goingToAsc) -h / 2 else h / 2
                                },
                            initialContentExit = fadeOut(tween(160)) +
                                slideOutVertically(tween(200)) { h ->
                                    if (goingToAsc) h / 2 else -h / 2
                                },
                            sizeTransform = SizeTransform(clip = false),
                        )
                    },
                    label = "sort-icon",
                ) { order ->
                    HaloIcon(
                        imageVector = when (order) {
                            GallerySortOrder.DateDesc -> PhosphorIcons.Bold.SortDescending
                            GallerySortOrder.DateAsc -> PhosphorIcons.Bold.SortAscending
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (selectedTab == GalleryTab.Albums) {
            CircularFloatingButton(
                onClick = onCreateAlbum,
                contentDescription = stringResource(R.string.new_album),
                backdrop = backdrop,
                componentStyle = componentStyle,
                glassTint = glassTint,
            ) {
                HaloIcon(
                    imageVector = PhosphorIcons.Bold.Plus,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * The tab switcher: a custom segmented pill (Settings | Gallery | Albums |
 * Tools) over a liquid-glass background. The selected segment is a denser glass
 * chip on top of the glass track rather than a primary-colored fill.
 *
 * Segments wrap their content with a small (2dp) gap. The pill has a fixed width
 * and scrolls the selected tab to its center, so the middle tabs peek
 * symmetrically on both sides.
 *
 * @param selectedTab the currently selected tab.
 * @param onSelectTab invoked when a segment is tapped.
 * @param backdrop the shared backdrop source for the glass surfaces.
 * @param componentStyle the resolved surface style.
 * @param glassTint the veil color drawn over the track.
 * @param modifier the modifier applied to the pill track.
 * @param onNavBarDrag invoked with the raw horizontal drag delta.
 * @param onNavBarDragStart invoked when a drag begins.
 * @param onNavBarDragEnd invoked when a drag ends.
 */
@Composable
private fun TabSwitcher(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    glassTint: Color,
    modifier: Modifier = Modifier,
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
) {
    val entries = listOf(
        GalleryTab.Settings to stringResource(R.string.tab_settings),
        GalleryTab.Gallery to stringResource(R.string.tab_gallery),
        GalleryTab.Albums to stringResource(R.string.tab_albums),
        GalleryTab.Tools to stringResource(R.string.tab_tools),
    )
    val selectedIndex = entries.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val tabWidths = remember { mutableStateListOf(0, 0, 0, 0) }

    val baseTrackModifier = modifier
        .width(PillWidth)
        .height(FloatingButtonSize)
        .clip(CircleShape)
    val trackModifier = if (componentStyle.drawsBackdrop()) {
        baseTrackModifier.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                if (componentStyle.usesBlur()) {
                    blur(GlassBlurRadius.toPx())
                }
                if (componentStyle.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        val trackFill = if (componentStyle == ComponentStyle.FROSTED) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        baseTrackModifier.background(trackFill)
    }

    LaunchedEffect(selectedIndex, tabWidths.toList()) {
        if (tabWidths.all { it > 0 }) {
            val viewportPx = with(density) { PillWidth.toPx() }
            val gapPx = with(density) { TabGap.toPx() }
            var center = 0f
            for (j in 0 until selectedIndex) center += tabWidths[j] + gapPx
            center += tabWidths[selectedIndex] / 2f
            val target = (center - viewportPx / 2f).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
        }
    }

    val measured = tabWidths.all { it > 0 }
    val insetPx = with(density) { SelectionInset.toPx() }
    val gapPx2 = with(density) { TabGap.toPx() }
    var chipLeftPx = 0f
    for (j in 0 until selectedIndex) chipLeftPx += tabWidths[j] + gapPx2
    val targetChipX = (chipLeftPx + insetPx).toInt()
    val targetChipWidth = (tabWidths[selectedIndex] - insetPx * 2f).toInt().coerceAtLeast(0)

    val chipReady = remember { mutableStateOf(false) }
    LaunchedEffect(measured) { if (measured) chipReady.value = true }
    val chipSpec = if (chipReady.value) tween(340, easing = FastOutSlowInEasing) else snap<Int>()
    val chipX by animateIntAsState(targetChipX, chipSpec, label = "tab-chip-x")
    val chipWidth by animateIntAsState(targetChipWidth, chipSpec, label = "tab-chip-w")

    val onNavBarDragState = rememberUpdatedState(onNavBarDrag)
    val onNavBarDragStartState = rememberUpdatedState(onNavBarDragStart)
    val onNavBarDragEndState = rememberUpdatedState(onNavBarDragEnd)

    Box(
        modifier = trackModifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { onNavBarDragStartState.value() },
                onDragEnd = { onNavBarDragEndState.value() },
                onDragCancel = { onNavBarDragEndState.value() },
            ) { change, dragAmount ->
                onNavBarDragState.value(dragAmount)
                change.consume()
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false),
        ) {
            if (measured) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(chipX, 0) }
                        .padding(vertical = SelectionInset)
                        .width(with(density) { chipWidth.toDp() })
                        .fillMaxHeight()
                        .selectedChipModifier(backdrop, componentStyle),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { index, (tab, label) ->
                    if (index > 0) Spacer(Modifier.width(TabGap))
                    PillSegment(
                        label = label,
                        selected = selectedTab == tab,
                        modifier = Modifier
                            .onSizeChanged { tabWidths[index] = it.width }
                            .padding(SelectionInset),
                    ) { onSelectTab(tab) }
                }
            }
        }
    }
}

/**
 * A single text segment inside the tab pill.
 *
 * The selected indicator is no longer drawn here — a single sliding chip owns it
 * (see [TabSwitcher]) so moving from A to B reads as motion rather than
 * appear/disappear. This segment only handles the text (with animated color),
 * the click area, and width measurement.
 *
 * @param label the segment label.
 * @param selected whether this segment is the selected tab.
 * @param modifier the modifier applied to the segment.
 * @param onClick invoked when the segment is tapped.
 */
@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(260),
        label = "segment-fg",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    offset = Offset.Zero,
                    blurRadius = TextHaloBlur,
                ),
            ),
            maxLines = 1,
        )
    }
}

/**
 * Builds the modifier for the selected-chip indicator (liquid-glass GLASS,
 * frosted, or solid), used by the sliding chip in [TabSwitcher].
 *
 * @param backdrop the shared backdrop source for glass rendering.
 * @param componentStyle the resolved surface style.
 * @return the modifier that paints the selected chip.
 */
@Composable
private fun Modifier.selectedChipModifier(
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
): Modifier {
    val selectedGlassTint =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (componentStyle == ComponentStyle.FROSTED) {
                SelectedFrostedHazeAlpha
            } else {
                SelectedGlassTintAlpha
            },
        )
    val selectedFrosted =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedFrostedAlpha)
    val selectedSolid = MaterialTheme.colorScheme.surfaceContainerHighest

    return when {
        componentStyle.drawsBackdrop() -> this.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                if (componentStyle.usesBlur()) {
                    blur(GlassBlurRadius.toPx())
                }
                if (componentStyle.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(selectedGlassTint) },
        )
        else -> this.clip(CircleShape).background(
            if (componentStyle == ComponentStyle.FROSTED) selectedFrosted else selectedSolid,
        )
    }
}

/**
 * An icon with a thin halo/outline behind it (a blurred copy tinted with the
 * surface color) so it stays readable over liquid glass even when the content
 * behind is a similar color. The blur is a no-op below API 31, where buttons
 * fall back to a denser fill.
 *
 * @param imageVector the icon to draw.
 * @param modifier the modifier applied to both icon layers.
 */
@Composable
private fun HaloIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = modifier.blur(
                radius = IconHaloBlur,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            ),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = modifier,
        )
    }
}

/**
 * A circular floating button.
 *
 * - API 33+: liquid glass (Kyant `drawBackdrop` + blur), refracting the content
 *   grid behind it. Lens refraction is intentionally omitted on these small
 *   round buttons because the Kyant kernel produces "hexagon" artifacts there;
 *   FROSTED omits blur too, leaving only the haze veil, so no hexagon appears.
 * - API < 33: a semi-transparent tonal `Surface` fallback (solid frosted).
 *
 * @param onClick invoked when the button is tapped.
 * @param contentDescription the accessibility description.
 * @param backdrop the shared backdrop source for glass rendering.
 * @param componentStyle the resolved surface style.
 * @param glassTint the veil color drawn over the glass.
 * @param modifier the modifier applied to the button.
 * @param content the button content (typically an icon).
 */
@Composable
private fun CircularFloatingButton(
    onClick: () -> Unit,
    contentDescription: String,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    glassTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (componentStyle.drawsBackdrop()) {
        Box(
            modifier = modifier
                .size(FloatingButtonSize)
                .clip(CircleShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        if (componentStyle.usesBlur()) {
                            blur(GlassBlurRadius.toPx())
                        }
                    },
                    onDrawSurface = { drawRect(glassTint) },
                )
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                content()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (componentStyle == ComponentStyle.FROSTED) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = modifier
                .size(FloatingButtonSize)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
