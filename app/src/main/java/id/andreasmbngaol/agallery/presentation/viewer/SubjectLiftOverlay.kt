package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Copy
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.kyant.backdrop.Backdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * iOS-style "lift subject" overlay shown on top of the viewer.
 *
 * - [SubjectLiftState.Processing] shows only a soft, organic multi-color glow
 *   that flows around the edges (Gemini-like): several blurred light blobs drift
 *   along the border at different speeds, wobble, and pulse, so nothing looks
 *   static. No text/spinner, and it never blocks touches -- the press gesture is
 *   owned by the page, so the lift auto-cancels the moment the finger is lifted
 *   while still processing.
 * - [SubjectLiftState.Lifted] shows the transparent cutout, which the user can
 *   drag, with Copy / Share floating just above the subject and following it.
 */
@Composable
fun SubjectLiftOverlay(
    state: SubjectLiftState,
    offset: Offset,
    style: ComponentStyle,
    backdrop: Backdrop,
    onDrag: (Offset) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SubjectLiftState.Idle -> Unit
        SubjectLiftState.Processing -> ProcessingShimmerBorder(modifier)
        is SubjectLiftState.Lifted -> LiftedContent(
            state = state,
            offset = offset,
            style = style,
            backdrop = backdrop,
            onDrag = onDrag,
            onCopy = onCopy,
            onShare = onShare,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

/**
 * One sine component of the flowing border wave. All frequencies are integers
 * (crests per full loop / laps per time cycle) so the whole ribbon animates
 * seamlessly with the shared 0..1 time driver -- there is no visible seam at the
 * wrap point.
 *
 * @property freq how many wave crests fit around the whole perimeter.
 * @property amp crest height in pixels, added along the inward normal.
 * @property speed how many times the wave travels around per time cycle
 *   (negative travels the other way).
 * @property phase constant offset so stacked waves do not line up.
 */
private data class Wave(
    val freq: Int,
    val amp: Float,
    val speed: Int,
    val phase: Float,
)

@Composable
private fun ProcessingShimmerBorder(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "lift-shimmer")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "lift-time",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420),
        label = "lift-appear",
    )

    val palette = remember {
        listOf(
            Color(0xFF4285F4),
            Color(0xFF9B72CB),
            Color(0xFFD96570),
            Color(0xFFF9AB55),
            Color(0xFF1CC6C6),
            Color(0xFF5B8DEF),
        )
    }
    val ringColors = remember(palette) { palette + palette.first() }

    // Two ribbons, each the sum of integer-frequency sine waves travelling around
    // the edge at different speeds -- so the light reads as flowing, rippling
    // waves rather than separate dots.
    val ribbonA = remember(density) {
        with(density) {
            listOf(
                Wave(freq = 5, amp = 12.dp.toPx(), speed = 2, phase = 0f),
                Wave(freq = 9, amp = 7.dp.toPx(), speed = 3, phase = 1.7f),
                Wave(freq = 15, amp = 3.5.dp.toPx(), speed = 5, phase = 3.1f),
            )
        }
    }
    val ribbonB = remember(density) {
        with(density) {
            listOf(
                Wave(freq = 6, amp = 11.dp.toPx(), speed = -2, phase = 2.2f),
                Wave(freq = 11, amp = 6.dp.toPx(), speed = 3, phase = 0.6f),
                Wave(freq = 4, amp = 4.dp.toPx(), speed = -3, phase = 4.0f),
            )
        }
    }

    val cornerRadius = with(density) { 48.dp.toPx() }
    val minDepth = with(density) { 3.dp.toPx() }
    val maxDepth = with(density) { 42.dp.toPx() }
    val glowBlur = 13.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .blur(glowBlur, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            .drawWithCache {
                val edgeRect = Rect(0f, 0f, size.width, size.height)
                val layers = 8
                onDrawBehind {
                    val brush = Brush.sweepGradient(
                        *sweepStops(ringColors, time),
                        center = size.center,
                    )
                    // Stack many faint wavy curtains from the very edge inward.
                    // Every curtain fills from the screen edge to its own depth,
                    // so the near-edge area is covered by all of them and the deep
                    // area by only a few -- the light is strongest at the edge and
                    // fades smoothly toward the center. The final blur melts the
                    // layers into one soft, organic bloom with a gently rippling
                    // inner front.
                    for (k in 0 until layers) {
                        val f = k / (layers - 1f)
                        val depth = minDepth + (maxDepth - minDepth) * f
                        val waves = if (k % 2 == 0) ribbonA else ribbonB
                        val contour = buildWaveContour(edgeRect, cornerRadius, waves, time, depth)
                        val band = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(edgeRect)
                            addPath(contour)
                        }
                        drawPath(band, brush, alpha = 0.05f * appear)
                    }
                }
            },
    )
}

/**
 * Builds the closed inner boundary of an edge glow: it walks [rect]'s perimeter
 * (rounded by [cornerRadius]) and pushes each of the [samples] points inward
 * along the local normal by [baseDepth] plus the summed [waves]. Filling the
 * area between the screen edge and this contour yields a glow that appears to
 * rise from the edge with a rippling wave front. Integer wave frequencies keep
 * the loop seamless.
 */
private fun buildWaveContour(
    rect: Rect,
    cornerRadius: Float,
    waves: List<Wave>,
    time: Float,
    baseDepth: Float,
    samples: Int = 240,
): Path {
    val twoPi = (2.0 * PI).toFloat()
    val path = Path()
    for (i in 0..samples) {
        val t = i.toFloat() / samples
        val (point, normal) = perimeterPointAndNormal(rect, cornerRadius, t)
        var disp = 0f
        for (w in waves) {
            disp += w.amp * sin(twoPi * w.freq * t + time * twoPi * w.speed + w.phase)
        }
        val depth = (baseDepth + disp).coerceAtLeast(0f)
        val x = point.x + normal.x * depth
        val y = point.y + normal.y * depth
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Point at fraction [t] (0..1) walking clockwise around a rounded rectangle with
 * corner radius [cornerRadius] inside [rect], together with the unit normal that
 * points inward (toward the center). Straight edges use axis-aligned normals;
 * the four rounded corners sweep the normal smoothly so the ribbon has no kinks.
 */
private fun perimeterPointAndNormal(
    rect: Rect,
    cornerRadius: Float,
    t: Float,
): Pair<Offset, Offset> {
    val r = cornerRadius.coerceAtMost(min(rect.width, rect.height) / 2f)
    val straightW = (rect.width - 2f * r).coerceAtLeast(0f)
    val straightH = (rect.height - 2f * r).coerceAtLeast(0f)
    val quarter = (PI.toFloat() / 2f) * r
    val total = 2f * (straightW + straightH) + 4f * quarter
    var d = ((t % 1f) + 1f) % 1f * total

    // Top edge (left -> right); inward normal points down.
    if (d <= straightW) {
        return Offset(rect.left + r + d, rect.top) to Offset(0f, 1f)
    }
    d -= straightW
    // Top-right corner.
    if (d <= quarter) {
        val a = if (r > 0f) d / r else 0f
        val nx = sin(a)
        val ny = -cos(a)
        return Offset(rect.right - r + r * nx, rect.top + r + r * ny) to Offset(-nx, -ny)
    }
    d -= quarter
    // Right edge (top -> bottom); inward normal points left.
    if (d <= straightH) {
        return Offset(rect.right, rect.top + r + d) to Offset(-1f, 0f)
    }
    d -= straightH
    // Bottom-right corner.
    if (d <= quarter) {
        val a = if (r > 0f) d / r else 0f
        val nx = cos(a)
        val ny = sin(a)
        return Offset(rect.right - r + r * nx, rect.bottom - r + r * ny) to Offset(-nx, -ny)
    }
    d -= quarter
    // Bottom edge (right -> left); inward normal points up.
    if (d <= straightW) {
        return Offset(rect.right - r - d, rect.bottom) to Offset(0f, -1f)
    }
    d -= straightW
    // Bottom-left corner.
    if (d <= quarter) {
        val a = if (r > 0f) d / r else 0f
        val nx = -sin(a)
        val ny = cos(a)
        return Offset(rect.left + r + r * nx, rect.bottom - r + r * ny) to Offset(-nx, -ny)
    }
    d -= quarter
    // Left edge (bottom -> top); inward normal points right.
    if (d <= straightH) {
        return Offset(rect.left, rect.bottom - r - d) to Offset(1f, 0f)
    }
    d -= straightH
    // Top-left corner.
    val a = if (r > 0f) (d / r).coerceIn(0f, PI.toFloat() / 2f) else 0f
    val nx = -cos(a)
    val ny = -sin(a)
    return Offset(rect.left + r + r * nx, rect.top + r + r * ny) to Offset(-nx, -ny)
}

@Composable
private fun LiftedContent(
    state: SubjectLiftState.Lifted,
    offset: Offset,
    style: ComponentStyle,
    backdrop: Backdrop,
    onDrag: (Offset) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        appeared = true
    }
    val pop by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.92f,
        animationSpec = tween(220),
        label = "lift-pop",
    )
    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(220),
        label = "lift-fade",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f * fade)),
        )

        Image(
            bitmap = state.cutout,
            contentDescription = stringResource(R.string.lift_subject_cd),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = pop
                    scaleY = pop
                    alpha = fade
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(Offset(drag.x, drag.y))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        )

        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val bw = state.cutout.width.toFloat()
        val bh = state.cutout.height.toFloat()
        val scale = min(cw / bw, ch / bh)
        val dispW = bw * scale
        val dispH = bh * scale
        val originX = (cw - dispW) / 2f
        val originY = (ch - dispH) / 2f
        val subjectTopPx = originY + state.subjectTopFraction * dispH + offset.y
        val subjectCenterXPx = originX + state.subjectCenterXFraction * dispW + offset.x
        val gapPx = with(density) { 14.dp.toPx() }
        val topInsetPx = with(density) { 28.dp.toPx() }
        var rowSize by remember { mutableStateOf(IntSize.Zero) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = fade }
                .offset {
                    val x = (subjectCenterXPx - rowSize.width / 2f)
                        .coerceIn(0f, (cw - rowSize.width).coerceAtLeast(0f))
                    val y = (subjectTopPx - gapPx - rowSize.height)
                        .coerceIn(topInsetPx, (ch - rowSize.height).coerceAtLeast(topInsetPx))
                    IntOffset(x.roundToInt(), y.roundToInt())
                }
                .onSizeChanged { rowSize = it },
        ) {
            GlassActionButton(
                text = stringResource(R.string.action_copy),
                onClick = onCopy,
                style = style,
                backdrop = backdrop,
                leadingIcon = PhosphorIcons.Bold.Copy,
            )
            GlassActionButton(
                text = stringResource(R.string.action_share),
                onClick = onShare,
                style = style,
                backdrop = backdrop,
                leadingIcon = PhosphorIcons.Bold.ShareNetwork,
            )
        }
    }
}

/**
 * Builds sweep-gradient color stops for a seamless, rotating multi-color ring.
 * Endpoints share the same interpolated color so there is no visible seam.
 */
private fun sweepStops(
    colors: List<Color>,
    phase: Float,
    samples: Int = 24,
): Array<Pair<Float, Color>> {
    val segments = colors.size - 1
    return Array(samples + 1) { k ->
        val s = k.toFloat() / samples
        val u = ((s + phase) % 1f + 1f) % 1f
        val pos = u * segments
        val i = pos.toInt().coerceIn(0, segments - 1)
        val f = pos - i
        s to lerp(colors[i], colors[i + 1], f)
    }
}
