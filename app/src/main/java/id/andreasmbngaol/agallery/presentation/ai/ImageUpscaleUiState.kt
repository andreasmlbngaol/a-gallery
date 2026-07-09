package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the Image Upscaler screen.
 *
 * Mirrors [BackgroundRemoverUiState] but has no quality selector: the upscale
 * models are fixed-input 4x models, so there is no per-run resolution trade-off.
 *
 * @property sourceUri the image being enhanced.
 * @property sourceDisplayName the original file name, used to derive the saved name.
 * @property installedModels installed upscale models the user can run, in display order.
 * @property selectedModelId the model chosen to run, or null to use the default.
 * @property resultPath the enlarged preview PNG path once produced, else null.
 * @property processing whether inference is currently running.
 * @property saving whether the result is currently being saved to the gallery.
 * @property componentStyleChosen chosen component style, or null while loading.
 * @property edgeEffectMode chosen edge-effect mode, or null while loading.
 * @property checkingModels whether the installed-model check is still running.
 * @property processingElapsedSeconds seconds elapsed in the current run, shown live.
 * @property processingUsedMemoryBytes process memory (PSS) sampled during the run.
 * @property processingCompletedTiles tiles finished so far in the current run.
 * @property processingTotalTiles total tiles the current run is split into (0 until known).
 * @property processingEtaSeconds estimated seconds remaining, or -1 while still estimating.
 */
data class ImageUpscaleUiState(
    val sourceUri: String = "",
    val sourceDisplayName: String = "",
    val installedModels: List<AiModelSpec> = emptyList(),
    val selectedModelId: AiModelId? = null,
    val selectedMode: UpscaleMode = UpscaleMode.AUTO,
    val resultPath: String? = null,
    val processing: Boolean = false,
    val saving: Boolean = false,
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
    val checkingModels: Boolean = true,
    val processingElapsedSeconds: Int = 0,
    val processingUsedMemoryBytes: Long = 0L,
    val processingCompletedTiles: Int = 0,
    val processingTotalTiles: Int = 0,
    val processingEtaSeconds: Int = -1,
) {
    /** Whether at least one upscale model is installed and usable. */
    val hasModel: Boolean get() = installedModels.isNotEmpty()
}
