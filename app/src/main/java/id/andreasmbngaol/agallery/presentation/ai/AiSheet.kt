package id.andreasmbngaol.agallery.presentation.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.MagnifyingGlassPlus
import com.adamglin.phosphoricons.bold.Smiley
import com.adamglin.phosphoricons.bold.Sparkle
import com.adamglin.phosphoricons.bold.UserRectangle
import id.andreasmbngaol.agallery.R

/**
 * Bottom sheet of on-device AI actions for a photo, opened from the viewer's AI
 * button. It lists the available on-device AI actions (Remove background,
 * Upscale image, Restore faces, Enhance photo) and is structured as a list so
 * future AI tools can be added without reshaping the UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(
    onEnhancePhoto: () -> Unit,
    onRemoveBackground: () -> Unit,
    onUpscaleImage: () -> Unit,
    onRestoreFaces: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            AiActionRow(
                // Subject lifted out of its background rectangle — reads as
                // "separate the person from the background" better than scissors.
                icon = PhosphorIcons.Bold.UserRectangle,
                title = stringResource(R.string.action_remove_background),
                onClick = onRemoveBackground,
            )
            AiActionRow(
                // Magnifier with a plus — clearly "enlarge / increase detail".
                icon = PhosphorIcons.Bold.MagnifyingGlassPlus,
                title = stringResource(R.string.action_upscale_image),
                onClick = onUpscaleImage,
            )
            AiActionRow(
                // A smiling face — simple and unmistakably about faces.
                icon = PhosphorIcons.Bold.Smiley,
                title = stringResource(R.string.action_restore_faces),
                onClick = onRestoreFaces,
            )
            AiActionRow(
                // A sparkle — the general "clean up / make this look better"
                // action; enhances the whole photo (denoise + restore detail).
                icon = PhosphorIcons.Bold.Sparkle,
                title = stringResource(R.string.action_enhance_photo),
                onClick = onEnhancePhoto,
            )
        }
    }
}

@Composable
private fun AiActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = PhosphorIcons.Bold.CaretRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
