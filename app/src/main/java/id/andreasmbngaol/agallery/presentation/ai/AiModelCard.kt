package id.andreasmbngaol.agallery.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.adamglin.phosphoricons.bold.CheckCircle
import com.adamglin.phosphoricons.bold.DownloadSimple
import com.adamglin.phosphoricons.bold.FolderOpen
import com.adamglin.phosphoricons.bold.Trash
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability

/**
 * A single card holding every model for a feature (e.g. background removal),
 * with the individual models stacked and separated by dividers instead of each
 * living in its own card. Each model row shows its name, tier badge, a short
 * rationale, install status & size, and its contextual actions rendered as
 * icon-only buttons to match the rest of the app's chrome (import from device,
 * re-import, delete, and open the model's download page in a browser).
 *
 * While a model is importing, that row's actions are replaced by a progress bar
 * with a phase label; the other rows stay visible but their import/re-import
 * buttons are disabled by the caller so only one import runs at a time.
 */
@Composable
fun AiModelsCard(
    rows: List<AiModelRow>,
    importDisabled: Boolean,
    onImport: (AiModelRow) -> Unit,
    onDelete: (AiModelRow) -> Unit,
    onOpenDownloadPage: (AiModelRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                ModelRow(
                    row = row,
                    importDisabled = importDisabled,
                    onImport = { onImport(row) },
                    onDelete = { onDelete(row) },
                    onOpenDownloadPage = { onOpenDownloadPage(row) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelRow(
    row: AiModelRow,
    importDisabled: Boolean,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onOpenDownloadPage: () -> Unit,
) {
    val spec = row.spec
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (spec.recommended) {
                Badge(
                    text = stringResource(R.string.ai_model_recommended),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(
                text = stringResource(AiModelStrings.tierLabel(spec.tier)),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (row.isInstalled) {
                Icon(
                    imageVector = PhosphorIcons.Bold.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.ai_model_status_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.ai_model_status_not_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val suit = row.suitability
        if (suit == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_model_suitability_checking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val container = when (suit.rating) {
                    ModelSuitability.Rating.GOOD -> MaterialTheme.colorScheme.primaryContainer
                    ModelSuitability.Rating.SLOW -> MaterialTheme.colorScheme.tertiaryContainer
                    ModelSuitability.Rating.INSUFFICIENT_MEMORY -> MaterialTheme.colorScheme.errorContainer
                }
                val content = when (suit.rating) {
                    ModelSuitability.Rating.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
                    ModelSuitability.Rating.SLOW -> MaterialTheme.colorScheme.onTertiaryContainer
                    ModelSuitability.Rating.INSUFFICIENT_MEMORY -> MaterialTheme.colorScheme.onErrorContainer
                }
                Badge(
                    text = stringResource(AiModelStrings.suitabilityLabel(suit.rating)),
                    container = container,
                    content = content,
                )
            }
        }

        val rationaleRes = AiModelStrings.rationale(spec.id)
        if (rationaleRes != 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(rationaleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        if (row.isImporting) {
            Text(
                text = stringResource(
                    when (row.importPhase) {
                        ImportPhase.VERIFYING -> R.string.ai_model_verifying
                        else -> R.string.ai_model_importing
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sizeText = if (row.isInstalled && row.installedSizeBytes != null) {
                    formatBytes(row.installedSizeBytes)
                } else {
                    "~" + formatBytes(spec.approxSizeBytes)
                }
                Text(
                    text = stringResource(R.string.ai_model_size, sizeText),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (row.isInstalled) {
                    IconButton(onClick = onImport, enabled = !importDisabled) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.ArrowClockwise,
                            contentDescription = stringResource(R.string.ai_model_action_reimport),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.Trash,
                            contentDescription = stringResource(R.string.ai_model_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    FilledTonalIconButton(onClick = onImport, enabled = !importDisabled) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.FolderOpen,
                            contentDescription = stringResource(R.string.ai_model_action_import),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onOpenDownloadPage) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.DownloadSimple,
                        contentDescription = stringResource(R.string.ai_model_download_page),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Human-readable megabyte string (e.g. `179 MB`, `4.8 MB`). */
private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 10) "%.0f MB".format(mb) else "%.1f MB".format(mb)
}
