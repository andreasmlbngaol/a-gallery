package id.andreasmbngaol.agallery.presentation.ai

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Image
import com.adamglin.phosphoricons.bold.Sparkle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ModelTier
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.presentation.viewer.GlassActionButton
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import java.io.File
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Image Upscaler screen for a single image. If no upscale model is installed it
 * shows an empty state that sends the user to the AI models screen; otherwise it
 * offers a model chooser, runs on-device 4x super-resolution into a preview, and
 * saves that preview into the gallery as a NEW PNG (the original is untouched).
 *
 * Mirrors the Background Remover screen but has no quality selector and no
 * transparency checkerboard (the result is an opaque, enlarged photo).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageUpscaleScreen(
    mediaUri: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenAiModels: () -> Unit,
) {
    val viewModel: ImageUpscaleViewModel =
        koinViewModel(key = mediaUri) { parametersOf(mediaUri, displayName) }
    val state by viewModel.uiState.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(state.componentStyleChosen)
    val effectiveMode = rememberEffectiveEdgeEffectMode(state.edgeEffectMode)
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val resources = LocalResources.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel) {
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

    // When the result becomes available, scroll it into view so the user isn't
    // left staring at the source image thinking nothing happened while the
    // large result PNG is still being decoded for display.
    LaunchedEffect(state.resultPath, scrollState.maxValue) {
        if (state.resultPath != null && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                    text = stringResource(R.string.upscale_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { sourceModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
        val backdropModifier = if (componentStyle.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = backdropModifier
                .then(sourceModifier)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(
                    top = safeDrawing.calculateTopPadding() + ScreenTopBarHeight + 8.dp,
                    bottom = if (state.hasModel) 172.dp else 32.dp,
                )
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.checkingModels) {
                CheckingModelsState()
                return@Column
            }
            if (!state.hasModel) {
                NoModelState(onOpenAiModels = onOpenAiModels)
                return@Column
            }

            Text(
                text = stringResource(R.string.upscale_choose_model),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            ModelDropdown(
                models = state.installedModels,
                selectedModelId = state.selectedModelId,
                enabled = !state.processing,
                onSelect = { viewModel.selectModel(it) },
            )

            ModeDropdown(
                selectedMode = state.selectedMode,
                enabled = !state.processing,
                onSelect = { viewModel.selectMode(it) },
            )

            Text(
                text = stringResource(R.string.upscale_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AsyncImage(
                model = state.sourceUri,
                contentDescription = state.sourceDisplayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            if (state.resultPath != null) {
                Text(
                    text = stringResource(R.string.upscale_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BeforeAfterSlider(
                    beforeModel = state.sourceUri,
                    afterModel = File(state.resultPath!!),
                    beforeLabel = stringResource(R.string.upscale_before),
                    afterLabel = stringResource(R.string.upscale_after),
                )
                Text(
                    text = stringResource(R.string.upscale_compare_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.upscale_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.processing) {
                ProcessingDialog(
                    elapsedSeconds = state.processingElapsedSeconds,
                    usedMemoryBytes = state.processingUsedMemoryBytes,
                    completedTiles = state.processingCompletedTiles,
                    totalTiles = state.processingTotalTiles,
                    etaSeconds = state.processingEtaSeconds,
                    onCancel = viewModel::cancelUpscale,
                )
            }
        }

        if (state.hasModel) {
            BottomActionBar(
                showSave = state.resultPath != null,
                upscaleEnabled = !state.processing && !state.saving,
                saveEnabled = !state.saving && !state.processing,
                style = componentStyle,
                backdrop = backdrop,
                onUpscale = viewModel::upscale,
                onSave = viewModel::save,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<AiModelSpec>,
    selectedModelId: AiModelId?,
    enabled: Boolean,
    onSelect: (AiModelId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = models.firstOrNull { it.id == selectedModelId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.upscale_choose_model)) },
            supportingText = if (selected != null) {
                { Text(text = modelTierAndSpeed(selected.tier)) }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.displayName,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = modelTierAndSpeed(model.tier),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeDropdown(
    selectedMode: UpscaleMode,
    enabled: Boolean,
    onSelect: (UpscaleMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(upscaleModeLabel(selectedMode)),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.upscale_mode)) },
            supportingText = { Text(stringResource(upscaleModeDesc(selectedMode))) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            UpscaleMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(upscaleModeLabel(mode)),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(upscaleModeDesc(mode)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Localized "Tier \u00b7 speed" hint for an upscale model (e.g. "Light \u00b7 fast"). */
@Composable
private fun modelTierAndSpeed(tier: ModelTier): String =
    stringResource(AiModelStrings.tierLabel(tier)) +
        " \u00b7 " + stringResource(upscaleModelSpeed(tier))

private fun upscaleModelSpeed(tier: ModelTier): Int = when (tier) {
    ModelTier.LIGHT -> R.string.upscale_model_speed_light
    ModelTier.BALANCED -> R.string.upscale_model_speed_balanced
    ModelTier.HIGH_QUALITY -> R.string.upscale_model_speed_high
}

private fun upscaleModeLabel(mode: UpscaleMode): Int = when (mode) {
    UpscaleMode.AUTO -> R.string.upscale_mode_auto
    UpscaleMode.ENLARGE -> R.string.upscale_mode_enlarge
    UpscaleMode.ORIGINAL_SIZE -> R.string.upscale_mode_original
    UpscaleMode.FULL -> R.string.upscale_mode_full
}

private fun upscaleModeDesc(mode: UpscaleMode): Int = when (mode) {
    UpscaleMode.AUTO -> R.string.upscale_mode_auto_desc
    UpscaleMode.ENLARGE -> R.string.upscale_mode_enlarge_desc
    UpscaleMode.ORIGINAL_SIZE -> R.string.upscale_mode_original_desc
    UpscaleMode.FULL -> R.string.upscale_mode_full_desc
}

@Composable
private fun BottomActionBar(
    showSave: Boolean,
    upscaleEnabled: Boolean,
    saveEnabled: Boolean,
    style: id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle,
    backdrop: Backdrop,
    onUpscale: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSave) {
            GlassActionButton(
                text = stringResource(R.string.upscale_save),
                onClick = onSave,
                style = style,
                backdrop = backdrop,
                enabled = saveEnabled,
                leadingIcon = PhosphorIcons.Bold.Image,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassActionButton(
            text = stringResource(R.string.upscale_run),
            onClick = onUpscale,
            style = style,
            backdrop = backdrop,
            enabled = upscaleEnabled,
            leadingIcon = PhosphorIcons.Bold.Sparkle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingDialog(
    elapsedSeconds: Int,
    usedMemoryBytes: Long,
    completedTiles: Int,
    totalTiles: Int,
    etaSeconds: Int,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.upscale_processing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.upscale_processing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Encoding the full-res PNG after the last tile isn't part of the
                // tile count, so treat "all tiles done" as a final finishing phase
                // (indeterminate bar) instead of showing a stale 0s estimate. A
                // gentle indeterminate bar is also used during the brief
                // decode/model-load warm-up before the tile count is known.
                val finishing = totalTiles > 0 && completedTiles >= totalTiles
                if (totalTiles > 0 && !finishing) {
                    // Determinate progress tracking the completed-tile count. The
                    // earlier "jumps ahead then settles back" wasn't this bar's
                    // spring animation; it was two upscale runs writing the same
                    // meter (fixed in the ViewModel via a per-run token). With a
                    // single monotonic source the expressive wavy bar reads clean.
                    val fraction = (completedTiles.toFloat() / totalTiles).coerceIn(0f, 1f)
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            finishing -> stringResource(R.string.upscale_progress_finishing)
                            totalTiles > 0 -> stringResource(
                                R.string.upscale_progress_tiles,
                                completedTiles,
                                totalTiles,
                            )
                            else -> stringResource(R.string.upscale_progress_preparing)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            finishing -> ""
                            etaSeconds >= 0 -> stringResource(
                                R.string.upscale_progress_eta,
                                formatDurationShort(etaSeconds),
                            )
                            else -> stringResource(R.string.upscale_progress_estimating)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(
                        R.string.upscale_processing_stats,
                        elapsedSeconds,
                        formatUsedMemory(usedMemoryBytes),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/** Formats a duration in seconds as "1m 05s" (>= 1 min) or "45s". */
private fun formatDurationShort(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return if (s >= 60) {
        "%dm %02ds".format(Locale.US, s / 60, s % 60)
    } else {
        "${s}s"
    }
}

private fun formatUsedMemory(bytes: Long): String {
    if (bytes <= 0L) return "\u2014"
    val mb = bytes / (1024L * 1024L)
    return if (mb >= 1024L) {
        "%.1f GB".format(Locale.US, mb / 1024.0)
    } else {
        "$mb MB"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckingModelsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun NoModelState(onOpenAiModels: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = PhosphorIcons.Bold.Sparkle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.upscale_no_model_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.upscale_no_model_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenAiModels) {
            Text(stringResource(R.string.upscale_choose_model))
        }
    }
}
