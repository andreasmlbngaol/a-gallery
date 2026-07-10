package id.andreasmbngaol.agallery.presentation.ai

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import id.andreasmbngaol.agallery.domain.model.ai.FaceBox
import id.andreasmbngaol.agallery.domain.model.ai.ModelTier
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.presentation.viewer.GlassActionButton
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Face Restore screen for a single image. If no face-restore model is installed
 * it shows an empty state that sends the user to the AI models screen; otherwise
 * it offers a model chooser and a blend-strength slider, runs on-device GPEN
 * restoration on every detected face into a preview, and saves that preview into
 * the gallery as a NEW PNG (the original is untouched).
 *
 * Mirrors the Image Upscaler screen but replaces the output-size mode dropdown
 * with a strength slider and reports progress in faces rather than tiles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FaceRestoreScreen(
    mediaUri: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenAiModels: () -> Unit,
) {
    val viewModel: FaceRestoreViewModel =
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
    // result PNG is still being decoded for display.
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
                    text = stringResource(R.string.face_restore_title),
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
                text = stringResource(R.string.face_restore_choose_model),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            ModelDropdown(
                models = state.installedModels,
                selectedModelId = state.selectedModelId,
                enabled = !state.processing,
                onSelect = { viewModel.selectModel(it) },
            )

            StrengthSlider(
                strength = state.strength,
                enabled = !state.processing,
                onChange = { viewModel.setStrength(it) },
            )

            Text(
                text = stringResource(R.string.face_restore_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = state.sourceUri,
                    contentDescription = state.sourceDisplayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.detectedFaces.isNotEmpty() &&
                    state.sourceWidth > 0 &&
                    state.sourceHeight > 0
                ) {
                    FaceBoxesOverlay(
                        faces = state.detectedFaces,
                        imageWidth = state.sourceWidth,
                        imageHeight = state.sourceHeight,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (state.detectionDone) {
                Text(
                    text = if (state.detectedFaces.isEmpty()) {
                        stringResource(R.string.face_restore_faces_none)
                    } else {
                        stringResource(
                            R.string.face_restore_faces_detected,
                            state.detectedFaces.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.resultPath != null) {
                Text(
                    text = stringResource(R.string.face_restore_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BeforeAfterSlider(
                    beforeModel = state.sourceUri,
                    afterModel = File(state.resultPath!!),
                    beforeLabel = stringResource(R.string.face_restore_before),
                    afterLabel = stringResource(R.string.face_restore_after),
                )
                Text(
                    text = stringResource(R.string.face_restore_compare_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.face_restore_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.processing) {
                ProcessingDialog(
                    elapsedSeconds = state.processingElapsedSeconds,
                    usedMemoryBytes = state.processingUsedMemoryBytes,
                    completedFaces = state.processingCompletedFaces,
                    totalFaces = state.processingTotalFaces,
                    onCancel = viewModel::cancelRestore,
                )
            }
        }

        if (state.hasModel) {
            BottomActionBar(
                showSave = state.resultPath != null,
                // Only allow a run once detection has finished AND found at least
                // one face: restoring an image with no detected faces would just
                // fail, so the button stays disabled (with the "no faces" caption
                // under the preview explaining why).
                restoreEnabled = !state.processing &&
                    !state.saving &&
                    state.detectedFaces.isNotEmpty(),
                saveEnabled = !state.saving && !state.processing,
                style = componentStyle,
                backdrop = backdrop,
                onRestore = viewModel::restore,
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
            label = { Text(stringResource(R.string.face_restore_choose_model)) },
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

/**
 * Blend-strength slider (0..1). Higher blends the restored face in more strongly
 * (sharper but can look less natural / drift from the real identity); lower
 * keeps more of the original for a subtler result.
 */
@Composable
private fun StrengthSlider(
    strength: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val recommended = FaceRestoreUiState.RECOMMENDED_STRENGTH
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.face_restore_strength_title),
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
                text = stringResource(R.string.face_restore_strength_natural),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.face_restore_strength_max),
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
                    R.string.face_restore_strength_recommended,
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
                    Text(stringResource(R.string.face_restore_strength_use_recommended))
                }
            }
        }
        Text(
            text = stringResource(R.string.face_restore_strength_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Draws each detected face as a rounded bounding box over the source preview.
 * [faces] are normalized (0..1) to [imageWidth] x [imageHeight]; because the
 * image is shown with [ContentScale.Fit] inside a square, the boxes are mapped
 * onto the fitted (letterboxed) image rectangle rather than the whole canvas.
 */
@Composable
private fun FaceBoxesOverlay(
    faces: List<FaceBox>,
    imageWidth: Int,
    imageHeight: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        if (imageWidth <= 0 || imageHeight <= 0 || canvasW <= 0f || canvasH <= 0f) return@Canvas
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val canvasAspect = canvasW / canvasH
        // Fitted image rect inside the canvas (ContentScale.Fit letterboxing).
        val fittedW: Float
        val fittedH: Float
        if (imageAspect > canvasAspect) {
            fittedW = canvasW
            fittedH = canvasW / imageAspect
        } else {
            fittedH = canvasH
            fittedW = canvasH * imageAspect
        }
        val offsetX = (canvasW - fittedW) / 2f
        val offsetY = (canvasH - fittedH) / 2f
        val strokeWidth = 2.dp.toPx()
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        faces.forEach { f ->
            val left = offsetX + f.left * fittedW
            val top = offsetY + f.top * fittedH
            val right = offsetX + f.right * fittedW
            val bottom = offsetY + f.bottom * fittedH
            val w = (right - left).coerceAtLeast(0f)
            val h = (bottom - top).coerceAtLeast(0f)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

/** Localized "Tier \u00b7 speed" hint for a face-restore model. */
@Composable
private fun modelTierAndSpeed(tier: ModelTier): String =
    stringResource(AiModelStrings.tierLabel(tier)) +
        " \u00b7 " + stringResource(faceRestoreModelSpeed(tier))

private fun faceRestoreModelSpeed(tier: ModelTier): Int = when (tier) {
    ModelTier.LIGHT -> R.string.upscale_model_speed_light
    ModelTier.BALANCED -> R.string.upscale_model_speed_balanced
    ModelTier.HIGH_QUALITY -> R.string.upscale_model_speed_high
}

@Composable
private fun BottomActionBar(
    showSave: Boolean,
    restoreEnabled: Boolean,
    saveEnabled: Boolean,
    style: id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle,
    backdrop: Backdrop,
    onRestore: () -> Unit,
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
                text = stringResource(R.string.face_restore_save),
                onClick = onSave,
                style = style,
                backdrop = backdrop,
                enabled = saveEnabled,
                leadingIcon = PhosphorIcons.Bold.Image,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassActionButton(
            text = stringResource(R.string.face_restore_run),
            onClick = onRestore,
            style = style,
            backdrop = backdrop,
            enabled = restoreEnabled,
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
    completedFaces: Int,
    totalFaces: Int,
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
                        text = stringResource(R.string.face_restore_processing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.face_restore_processing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Determinate progress only makes sense with MORE THAN ONE face:
                // the bar advances one step per finished face. With a single face
                // there is nothing meaningful to fill toward, so a continuously
                // running indeterminate wavy bar reads better. The same
                // indeterminate bar also covers the warm-up (decode/detect/model
                // load, before the face count is known) and the final finishing
                // phase (encoding the full-res PNG after the last face, which
                // isn't part of the face count).
                val finishing = totalFaces in 1..completedFaces
                if (totalFaces > 1 && !finishing) {
                    val fraction = (completedFaces.toFloat() / totalFaces).coerceIn(0f, 1f)
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

                Text(
                    text = when {
                        finishing -> stringResource(R.string.face_restore_progress_finishing)
                        totalFaces > 1 -> stringResource(
                            R.string.face_restore_progress_faces,
                            completedFaces,
                            totalFaces,
                        )
                        totalFaces == 1 -> stringResource(R.string.face_restore_progress_single)
                        else -> stringResource(R.string.face_restore_progress_detecting)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

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
            text = stringResource(R.string.face_restore_no_model_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.face_restore_no_model_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenAiModels) {
            Text(stringResource(R.string.face_restore_choose_model))
        }
    }
}
