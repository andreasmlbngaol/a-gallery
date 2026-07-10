package id.andreasmbngaol.agallery.presentation.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretLeft
import com.adamglin.phosphoricons.bold.CaretRight

/**
 * Draggable before/after comparison shared by the single-image AI tools
 * (Enhance, Upscale, Face Restore). The [afterModel] (processed result) fills
 * the frame; the [beforeModel] (original) is drawn on top but clipped to the
 * left of the divider, so dragging the handle wipes between the two. Both use
 * [ContentScale.Fit] so the same pixels line up on each side of the divider.
 *
 * The draggable handle consumes drags in ALL directions (not just horizontal),
 * so a vertical component of the gesture can't leak up to a parent
 * verticalScroll and nudge the page while the user is comparing (which was the
 * "jumps up a little" glitch when interacting near the bottom of the screen).
 */
@Composable
fun BeforeAfterSlider(
    beforeModel: Any?,
    afterModel: Any?,
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        // After (processed) fills the frame.
        AsyncImage(
            model = afterModel,
            contentDescription = afterLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Before (original) drawn on top but clipped to the left of the divider.
        AsyncImage(
            model = beforeModel,
            contentDescription = beforeLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
        // Corner labels.
        CompareChip(
            text = beforeLabel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
        CompareChip(
            text = afterLabel,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
        // Divider + handle, positioned at the current fraction and draggable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    // Consume the WHOLE drag (both axes) so the vertical
                    // component can't bubble up to a parent scroll -- that leak
                    // was what made the page jump slightly while sliding.
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (widthPx > 0f) {
                            fraction = (fraction + dragAmount.x / widthPx).coerceIn(0f, 1f)
                        }
                    }
                },
        ) {
            val handleX = with(density) { (widthPx * fraction).toDp() }
            // Vertical divider line.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = handleX - 1.dp)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White),
            )
            // Round handle.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = handleX - 18.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.CaretLeft,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp),
                    )
                    Icon(
                        imageVector = PhosphorIcons.Bold.CaretRight,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
