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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.presentation.viewer.GlassActionButton
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import java.io.File
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Background Remover screen for a single image. If no model is installed it
 * shows an empty state that sends the user to the AI models screen; otherwise it
 * offers a model chooser, runs on-device removal into a transparent preview, and
 * saves that preview into the gallery as a NEW PNG (the original is untouched).
 *
 * [mediaUri] and [displayName] come from the navigation route and are passed to
 * the view model as Koin parameters.
 */
@Composable
fun BackgroundRemoverScreen(
    mediaUri: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenAiModels: () -> Unit,
) {
    val viewModel: BackgroundRemoverViewModel =
        koinViewModel(key = mediaUri) { parametersOf(mediaUri, displayName) }
    val state by viewModel.uiState.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(state.componentStyleChosen)
    val effectiveMode = rememberEffectiveEdgeEffectMode(state.edgeEffectMode)
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val resources = LocalResources.current

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
                    text = stringResource(R.string.bg_remover_title),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 88.dp, bottom = if (state.hasModel) 172.dp else 32.dp)
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
                text = stringResource(R.string.bg_remover_choose_model),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            ModelDropdown(
                models = state.installedModels,
                selectedModelId = state.selectedModelId,
                enabled = !state.processing,
                onSelect = { viewModel.selectModel(it) },
            )

            Text(
                text = stringResource(R.string.bg_remover_source),
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
                    text = stringResource(R.string.bg_remover_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .checkerboard(),
                ) {
                    AsyncImage(
                        model = File(state.resultPath!!),
                        contentDescription = stringResource(R.string.bg_remover_result),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = stringResource(R.string.bg_remover_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.processing) {
                ProcessingDialog(
                    elapsedSeconds = state.processingElapsedSeconds,
                    usedMemoryBytes = state.processingUsedMemoryBytes,
                )
            }
        }

        if (state.hasModel) {
            BottomActionBar(
                showSave = state.resultPath != null,
                removeEnabled = !state.processing && !state.saving,
                saveEnabled = !state.saving && !state.processing,
                style = componentStyle,
                backdrop = backdrop,
                onRemove = viewModel::removeBackground,
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
            label = { Text(stringResource(R.string.bg_remover_choose_model)) },
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
                    text = { Text(model.displayName) },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    showSave: Boolean,
    removeEnabled: Boolean,
    saveEnabled: Boolean,
    style: ComponentStyle,
    backdrop: Backdrop,
    onRemove: () -> Unit,
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
                text = stringResource(R.string.bg_remover_save),
                onClick = onSave,
                style = style,
                backdrop = backdrop,
                enabled = saveEnabled,
                leadingIcon = PhosphorIcons.Bold.Image,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassActionButton(
            text = stringResource(R.string.bg_remover_remove),
            onClick = onRemove,
            style = style,
            backdrop = backdrop,
            enabled = removeEnabled,
            leadingIcon = PhosphorIcons.Bold.Sparkle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Blocking loading dialog shown while on-device inference runs, so the action
 * button no longer needs an inline spinner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingDialog(elapsedSeconds: Int, usedMemoryBytes: Long) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(32.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.bg_remover_processing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.bg_remover_processing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.bg_remover_processing_stats,
                            elapsedSeconds,
                            formatUsedMemory(usedMemoryBytes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Formats process memory usage for the live inference meter (MB, or a dash). */
private fun formatUsedMemory(bytes: Long): String {
    if (bytes <= 0L) return "\u2014"
    val mb = bytes / (1024L * 1024L)
    return "$mb MB"
}

/**
 * Brief loading state shown while the installed-model check runs, so opening the
 * screen for an already-installed model no longer flashes the empty state.
 */
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
            text = stringResource(R.string.bg_remover_no_model_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.bg_remover_no_model_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenAiModels) {
            Text(stringResource(R.string.bg_remover_choose_model))
        }
    }
}

/**
 * Draws a light/grey checkerboard behind transparent content so the removed
 * background reads as "transparent" rather than white.
 */
private fun Modifier.checkerboard(cell: Float = 24f): Modifier = drawBehind {
    val light = Color(0xFFFFFFFF)
    val dark = Color(0xFFE0E0E0)
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            if ((row + col) % 2 == 0) {
                drawRect(
                    color = dark,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
            }
            x += cell
            col++
        }
        y += cell
        row++
    }
}
