package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the Photo Enhance screen.
 *
 * Combines the two patterns of the other single-image AI features: like the
 * Upscaler it reports progress in TILES (the model runs over the frame in an
 * overlapping grid), and like Face Restore it offers a blend [strength] (0..1)
 * rather than an output-size mode (Enhance keeps the original resolution).
 *
 * The two installed SCUNet models are a STYLE choice, not a speed/size tier:
 * one leans sharper/more detailed (GAN), the other cleaner/smoother (PSNR).
 *
 * @property sourceUri the image being enhanced.
 * @property sourceDisplayName the original file name, used to derive the saved name.
 * @property installedModels installed enhance models the user can run, in display order.
 * @property selectedModelId the model chosen to run, or null to use the default.
 * @property strength blend strength (0..1); higher is a stronger clean-up, lower keeps more original texture.
 * @property resultPath the enhanced preview PNG path once produced, else null.
 * @property processing whether inference is currently running.
 * @property saving whether the result is currently being saved to the gallery.
 * @property componentStyleChosen chosen component style, or null while loading.
 * @property edgeEffectMode chosen edge-effect mode, or null while loading.
 * @property checkingModels whether the installed-model check is still running.
 * @property processingElapsedSeconds seconds elapsed in the current run, shown live.
 * @property processingUsedMemoryBytes process memory (PSS) sampled during the run.
 * @property processingCompletedTiles tiles finished so far in the current run.
 * @property processingTotalTiles total tiles the current run is split into (0 until known).
 * @property processingEtaSeconds estimated seconds remaining in the current run, or -1 while still estimating.
 */
data class PhotoEnhanceUiState(
    val sourceUri: String = "",
    val sourceDisplayName: String = "",
    val installedModels: List<AiModelSpec> = emptyList(),
    val selectedModelId: AiModelId? = null,
    val strength: Float = DEFAULT_STRENGTH,
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
    /** Whether at least one enhance model is installed and usable. */
    val hasModel: Boolean get() = installedModels.isNotEmpty()

    companion object {
        /** Default blend strength: strong clean-up that still looks natural. */
        const val DEFAULT_STRENGTH = 0.8f

        /**
         * Blend strength recommended to the user in the UI. Matches
         * [DEFAULT_STRENGTH]: strong enough to clearly restore detail while
         * keeping the photo's natural texture.
         */
        const val RECOMMENDED_STRENGTH = DEFAULT_STRENGTH
    }
}
