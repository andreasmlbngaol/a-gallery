package id.andreasmbngaol.agallery.presentation.ai

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOptions
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStage
import id.andreasmbngaol.agallery.presentation.viewer.GlassActionButton
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Auto Enhance screen for a single image: the one-tap pipeline that chains Face
 * Restore -> Enhance -> Upscale. Because it runs three models it first gates on
 * ALL three being installed (spelling out which is missing and linking to the AI
 * models screen); otherwise it offers a per-stage on/off list and a single
 * shared strength slider, runs the pipeline on-device, and shows the result
 * STEP BY STEP (original, then each stage's output). Saving writes a NEW PNG
 * under Pictures/AGallery Auto Enhanced; the original is never modified.
 *
 * Reuses the Photo Enhance screen's chrome, strength slider and tile-progress
 * dialog, adding a stage checklist and a stepped result list on top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoEnhanceScreen(
    mediaUri: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenAiModels: () -> Unit,
) {
    val viewModel: AutoEnhanceViewModel =
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

    // Scroll the freshly produced steps into view so the user sees the result
    // instead of being left at the source image.
    LaunchedEffect(state.finalPath, scrollState.maxValue) {
        if (state.finalPath != null && scrollState.maxValue > 0) {
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
                    text = stringResource(R.string.auto_enhance_title),
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
                        bottom = if (state.hasAllModels) 172.dp else 32.dp,
                    )
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.checkingModels) {
                    CheckingModelsState()
                    return@Column
                }
                if (!state.hasAllModels) {
                    GateState(
                        hasFaceModel = state.hasFaceModel,
                        hasEnhanceModel = state.hasEnhanceModel,
                        hasUpscaleModel = state.hasUpscaleModel,
                        onOpenAiModels = onOpenAiModels,
                    )
                    return@Column
                }

                Text(
                    text = stringResource(R.string.auto_enhance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.auto_enhance_stages_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StageToggleRow(
                    title = stringResource(R.string.auto_enhance_stage_face),
                    description = stringResource(R.string.auto_enhance_toggle_face_desc),
                    checked = state.runFaceRestore,
                    enabled = !state.processing,
                    onCheckedChange = viewModel::setFaceRestore,
                )
                AnimatedVisibility(
                    visible = state.runFaceRestore,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    StrengthSlider(
                        title = stringResource(R.string.auto_enhance_face_strength_title),
                        caption = stringResource(R.string.auto_enhance_face_strength_desc),
                        strength = state.faceStrength,
                        recommended = AutoEnhanceOptions.FACE_RECOMMENDED_STRENGTH,
                        enabled = !state.processing,
                        onChange = viewModel::setFaceStrength,
                    )
                }

                StageToggleRow(
                    title = stringResource(R.string.auto_enhance_stage_enhance),
                    description = stringResource(R.string.auto_enhance_toggle_enhance_desc),
                    checked = state.runEnhance,
                    enabled = !state.processing,
                    onCheckedChange = viewModel::setEnhance,
                )
                AnimatedVisibility(
                    visible = state.runEnhance,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    StrengthSlider(
                        title = stringResource(R.string.auto_enhance_enhance_strength_title),
                        caption = stringResource(R.string.auto_enhance_enhance_strength_desc),
                        strength = state.enhanceStrength,
                        recommended = AutoEnhanceOptions.ENHANCE_RECOMMENDED_STRENGTH,
                        enabled = !state.processing,
                        onChange = viewModel::setEnhanceStrength,
                    )
                }

                StageToggleRow(
                    title = stringResource(R.string.auto_enhance_stage_upscale),
                    description = stringResource(R.string.auto_enhance_toggle_upscale_desc),
                    checked = state.runUpscale,
                    enabled = !state.processing,
                    onCheckedChange = viewModel::setUpscale,
                )
                AnimatedVisibility(
                    visible = state.runUpscale,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    StrengthSlider(
                        title = stringResource(R.string.auto_enhance_upscale_strength_title),
                        caption = stringResource(R.string.auto_enhance_upscale_strength_desc),
                        strength = state.upscaleStrength,
                        recommended = AutoEnhanceOptions.UPSCALE_RECOMMENDED_STRENGTH,
                        enabled = !state.processing,
                        onChange = viewModel::setUpscaleStrength,
                    )
                }

                if (state.finalPath == null) {
                    Text(
                        text = stringResource(R.string.auto_enhance_source),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PreviewImage(model = state.sourceUri, contentDescription = state.sourceDisplayName)
                } else {
                    Text(
                        text = stringResource(R.string.auto_enhance_steps_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StepCard(
                        label = stringResource(R.string.auto_enhance_source),
                        model = state.sourceUri,
                        contentDescription = state.sourceDisplayName,
                    )
                    if (state.faceSkipped) {
                        Text(
                            text = stringResource(R.string.auto_enhance_faces_skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.stageResults.forEachIndexed { index, result ->
                        val isFinal = index == state.stageResults.lastIndex
                        val label = if (isFinal) {
                            stringResource(
                                R.string.auto_enhance_final_label,
                                stringResource(stageLabelRes(result.stage)),
                            )
                        } else {
                            stringResource(stageLabelRes(result.stage))
                        }
                        StepCard(
                            label = label,
                            model = File(result.resultPath),
                            contentDescription = label,
                        )
                    }
                    Text(
                        text = stringResource(R.string.auto_enhance_saved_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.processing) {
                    StepProgressDialog(
                        stepNumber = state.currentStageNumber,
                        totalSteps = state.plannedStages.size,
                        stageLabel = state.currentStage?.let { stringResource(stageLabelRes(it)) } ?: "",
                        elapsedSeconds = state.processingElapsedSeconds,
                        usedMemoryBytes = state.processingUsedMemoryBytes,
                        completedTiles = state.processingCompletedTiles,
                        totalTiles = state.processingTotalTiles,
                        etaSeconds = state.processingEtaSeconds,
                        onCancel = viewModel::cancel,
                    )
                }
            }

            if (state.hasAllModels) {
                BottomActionBar(
                    showSave = state.finalPath != null,
                    runEnabled = !state.processing && !state.saving && state.hasAnyStage,
                    saveEnabled = !state.saving && !state.processing,
                    style = componentStyle,
                    backdrop = backdrop,
                    onRun = viewModel::run,
                    onSave = viewModel::save,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Localized stage name. */
private fun stageLabelRes(stage: AutoEnhanceStage): Int = when (stage) {
    AutoEnhanceStage.FACE_RESTORE -> R.string.auto_enhance_stage_face
    AutoEnhanceStage.ENHANCE -> R.string.auto_enhance_stage_enhance
    AutoEnhanceStage.UPSCALE -> R.string.auto_enhance_stage_upscale
}

@Composable
private fun StageToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun PreviewImage(model: Any, contentDescription: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StepCard(label: String, model: Any, contentDescription: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        PreviewImage(model = model, contentDescription = contentDescription)
    }
}

@Composable
private fun GateState(
    hasFaceModel: Boolean,
    hasEnhanceModel: Boolean,
    hasUpscaleModel: Boolean,
    onOpenAiModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
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
            text = stringResource(R.string.auto_enhance_gate_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auto_enhance_gate_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        ModelStatusRow(stringResource(R.string.auto_enhance_stage_face), hasFaceModel)
        ModelStatusRow(stringResource(R.string.auto_enhance_stage_enhance), hasEnhanceModel)
        ModelStatusRow(stringResource(R.string.auto_enhance_stage_upscale), hasUpscaleModel)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenAiModels) {
            Text(stringResource(R.string.auto_enhance_gate_button))
        }
    }
}

@Composable
private fun ModelStatusRow(name: String, installed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (installed) {
                stringResource(R.string.auto_enhance_model_ready)
            } else {
                stringResource(R.string.auto_enhance_model_missing)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (installed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun BottomActionBar(
    showSave: Boolean,
    runEnabled: Boolean,
    saveEnabled: Boolean,
    style: id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle,
    backdrop: Backdrop,
    onRun: () -> Unit,
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
                text = stringResource(R.string.auto_enhance_save),
                onClick = onSave,
                style = style,
                backdrop = backdrop,
                enabled = saveEnabled,
                leadingIcon = PhosphorIcons.Bold.Image,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassActionButton(
            text = stringResource(R.string.auto_enhance_run),
            onClick = onRun,
            style = style,
            backdrop = backdrop,
            enabled = runEnabled,
            leadingIcon = PhosphorIcons.Bold.Sparkle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepProgressDialog(
    stepNumber: Int,
    totalSteps: Int,
    stageLabel: String,
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
                        text = stringResource(R.string.auto_enhance_processing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (stepNumber > 0 && totalSteps > 0 && stageLabel.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.auto_enhance_progress_step,
                                stepNumber,
                                totalSteps,
                                stageLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.auto_enhance_processing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))

                val finishing = totalTiles in 1..completedTiles
                if (totalTiles > 1 && !finishing) {
                    val fraction = (completedTiles.toFloat() / totalTiles).coerceIn(0f, 1f)
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            finishing -> stringResource(R.string.enhance_progress_finishing)
                            totalTiles > 1 -> stringResource(
                                R.string.enhance_progress_tiles,
                                completedTiles,
                                totalTiles,
                            )
                            else -> stringResource(R.string.enhance_progress_preparing)
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

/**
 * Per-stage blend-strength slider (0..1). Each Auto Enhance stage has its own
 * [recommended] default and [caption], so the same control is reused for Face
 * Restore, Enhance and Upscale. Mirrors the Photo Enhance slider but is
 * duplicated here because that one is file-private.
 */
@Composable
private fun StrengthSlider(
    title: String,
    caption: String,
    strength: Float,
    recommended: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(strength * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = strength,
            onValueChange = onChange,
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.enhance_strength_natural),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.enhance_strength_max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.enhance_strength_recommended,
                    (recommended * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (abs(strength - recommended) > 0.005f) {
                TextButton(
                    onClick = { onChange(recommended) },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.enhance_strength_use_recommended))
                }
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
