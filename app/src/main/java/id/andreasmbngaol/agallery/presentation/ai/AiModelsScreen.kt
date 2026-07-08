package id.andreasmbngaol.agallery.presentation.ai

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.CaretRight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import androidx.compose.ui.graphics.Color
import id.andreasmbngaol.agallery.core.ui.SegmentedGlassItem
import id.andreasmbngaol.agallery.core.ui.SegmentedGlassTrack
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import org.koin.androidx.compose.koinViewModel

/**
 * AI models management screen (reached from Settings). Lists every model in the
 * catalog with its install status, and lets the user import a `.onnx` file they
 * downloaded themselves (via the system file picker), re-import, or delete it.
 *
 * No weights are bundled and nothing is downloaded in-app: "Download page" opens
 * the model's web page in a browser, and the actual file is brought in through
 * the OS document picker. Chrome styling follows the app's Solid/Frosted/Glass
 * setting via [SystemBarScrim] and [GlassIconButton].
 */
@Composable
fun AiModelsScreen(
    onBack: () -> Unit,
) {
    val viewModel: AiModelsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(state.componentStyleChosen)
    val effectiveMode = rememberEffectiveEdgeEffectMode(state.edgeEffectMode)
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current

    var pendingSpec by remember { mutableStateOf<AiModelSpec?>(null) }
    var bgExpanded by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<AiModelRow?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val spec = pendingSpec
        pendingSpec = null
        if (uri != null && spec != null) {
            viewModel.import(spec, uri.toString())
        }
    }

    LaunchedEffectMessages(viewModel = viewModel)

    SystemBarScrim(
        mode = effectiveMode,
        topExtraHeight = ScreenTopBarHeight,
        topOverlay = {
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
                    onClick = onBack,
                    contentDescription = stringResource(R.string.action_back),
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
                    text = stringResource(R.string.ai_models_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { sourceModifier ->
        val backdropModifier = if (componentStyle.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = backdropModifier
                .then(sourceModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 88.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_models_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.deviceCapability?.let { cap ->
                Text(
                    text = stringResource(
                        R.string.ai_device_summary,
                        formatGb(cap.totalRamBytes),
                        stringResource(AiModelStrings.cpuClassLabel(cap.cpuScore)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FeatureSection(
                title = stringResource(R.string.ai_models_feature_bg),
                expanded = bgExpanded,
                onToggle = { bgExpanded = !bgExpanded },
            ) {
                val importing = state.rows.any { it.isImporting }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AiModelsCard(
                    rows = state.rows,
                    importDisabled = importing,
                    onImport = { row ->
                        pendingSpec = row.spec
                        importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onDelete = { row -> pendingDelete = row },
                    onOpenDownloadPage = { row -> openDownloadPage(context, row.spec.downloadUrl) },
                )
                LiftModelSelector(
                    installed = state.rows.filter { it.isInstalled },
                    selectedId = state.liftModelId,
                    onSelect = { id -> viewModel.selectLiftModel(id) },
                    quality = state.liftQuality,
                    onSelectQuality = { q -> viewModel.selectLiftQuality(q) },
                    componentStyle = componentStyle,
                )
                }
            }
            Text(
                text = stringResource(R.string.ai_model_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingDelete?.let { row ->
        DeleteModelDialog(
            modelName = row.spec.displayName,
            onConfirm = {
                viewModel.delete(row.spec)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * A collapsible section header (e.g. "Background removal") with its content. The
 * header toggles [expanded]; a caret rotates to indicate state. Keeping each
 * feature collapsible keeps the screen tidy as more AI modules are added.
 */
@Composable
private fun FeatureSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val caretRotation by animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            animationSpec = tween(200),
            label = "feature-caret",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = PhosphorIcons.Bold.CaretRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(caretRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        ) {
            content()
        }
    }
}

/** Collects one-shot view-model messages and shows them as toasts. */
@Composable
private fun LaunchedEffectMessages(viewModel: AiModelsViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val arg = message.formatArg
            val text = if (arg != null) {
                resources.getString(message.textRes, arg)
            } else {
                resources.getString(message.textRes)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Human-readable gigabyte string for total device RAM (e.g. `6.0 GB`). */
private fun formatGb(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return "%.1f GB".format(gb)
}

/** Opens the model's download web page in a browser (never downloads in-app). */
private fun openDownloadPage(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

/**
 * Confirmation dialog shown before deleting an installed model, so a tap on the
 * trash icon cannot remove a large file by accident.
 */
@Composable
private fun DeleteModelDialog(
    modelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_model_delete_confirm_title)) },
        text = { Text(stringResource(R.string.ai_model_delete_confirm_message, modelName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Lets the user pick which background-removal model powers the viewer's
 * long-press "lift subject" gesture, plus the Eco/Balanced/High quality for
 * models that support it. The choices mirror the models above; "Auto" uses the
 * smallest installed one (fastest). Each model shows this device's suitability
 * verdict (from the startup benchmark) so heavier models that would be slow or
 * run out of memory on this phone are clearly flagged.
 */
@Composable
private fun LiftModelSelector(
    installed: List<AiModelRow>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    quality: RemovalQuality,
    onSelectQuality: (RemovalQuality) -> Unit,
    componentStyle: ComponentStyle,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.ai_lift_model_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ai_lift_model_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (installed.isEmpty()) {
                Text(
                    text = stringResource(R.string.ai_lift_model_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LiftOption(
                    label = stringResource(R.string.ai_lift_model_auto),
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
                installed.forEach { row ->
                    val rating = row.suitability?.rating
                    val caption = if (row.suitability == null) {
                        stringResource(R.string.ai_model_suitability_checking)
                    } else {
                        stringResource(AiModelStrings.suitabilityLabel(rating!!))
                    }
                    val captionColor = when (rating) {
                        ModelSuitability.Rating.SLOW -> MaterialTheme.colorScheme.tertiary
                        ModelSuitability.Rating.INSUFFICIENT_MEMORY -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    LiftOption(
                        label = row.spec.displayName,
                        selected = selectedId == row.spec.id.value,
                        onClick = { onSelect(row.spec.id.value) },
                        trailing = stringResource(AiModelStrings.tierLabel(row.spec.tier)),
                        caption = caption,
                        captionColor = captionColor,
                    )
                }

                val selectedSpec =
                    installed.firstOrNull { it.spec.id.value == selectedId }?.spec
                if (selectedSpec?.offersQualityChoice == true) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ai_lift_quality_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    val qualityOptions = listOf(
                        RemovalQuality.ECO to R.string.bg_remover_quality_eco,
                        RemovalQuality.BALANCED to R.string.bg_remover_quality_balanced,
                        RemovalQuality.HIGH to R.string.bg_remover_quality_high,
                    )
                    SegmentedGlassTrack(componentStyle = componentStyle) {
                        qualityOptions.forEach { (q, labelRes) ->
                            SegmentedGlassItem(
                                label = stringResource(labelRes),
                                selected = quality == q,
                                onClick = { onSelectQuality(q) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.ai_lift_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
    caption: String? = null,
    captionColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (captionColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        captionColor
                    },
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
