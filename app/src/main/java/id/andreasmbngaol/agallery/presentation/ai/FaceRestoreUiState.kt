package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.FaceBox
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the Face Restore screen.
 *
 * Mirrors [ImageUpscaleUiState] but swaps the (absent) mode selector for a
 * blend [strength] (0..1): GPEN runs at a fixed resolution, so there is no
 * per-run size trade-off, only how strongly the restored face is blended over
 * the original. Progress is measured in FACES rather than tiles.
 *
 * @property sourceUri the image being restored.
 * @property sourceDisplayName the original file name, used to derive the saved name.
 * @property installedModels installed face-restore models the user can run, in display order.
 * @property selectedModelId the model chosen to run, or null to use the default.
 * @property strength blend strength (0..1); higher is sharper, lower is more natural.
 * @property resultPath the restored preview PNG path once produced, else null.
 * @property processing whether inference is currently running.
 * @property saving whether the result is currently being saved to the gallery.
 * @property componentStyleChosen chosen component style, or null while loading.
 * @property edgeEffectMode chosen edge-effect mode, or null while loading.
 * @property checkingModels whether the installed-model check is still running.
 * @property processingElapsedSeconds seconds elapsed in the current run, shown live.
 * @property processingUsedMemoryBytes process memory (PSS) sampled during the run.
 * @property processingCompletedFaces faces finished so far in the current run.
 * @property processingTotalFaces total faces detected in the current run (0 until known).
 * @property detectedFaces faces found in the source image, drawn as bounding boxes over the original preview.
 * @property sourceWidth decoded width of the source image the [detectedFaces] boxes are relative to (0 until known).
 * @property sourceHeight decoded height of the source image the [detectedFaces] boxes are relative to (0 until known).
 * @property detectionDone whether the one-off face detection on load has finished (regardless of how many faces were found).
 */
data class FaceRestoreUiState(
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
    val processingCompletedFaces: Int = 0,
    val processingTotalFaces: Int = 0,
    val detectedFaces: List<FaceBox> = emptyList(),
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val detectionDone: Boolean = false,
) {
    /** Whether at least one face-restore model is installed and usable. */
    val hasModel: Boolean get() = installedModels.isNotEmpty()

    companion object {
        /** Default blend strength: strong but still natural. */
        const val DEFAULT_STRENGTH = 0.8f

        /**
         * Blend strength recommended to the user in the UI. Matches
         * [DEFAULT_STRENGTH]: strong enough to clearly restore detail while
         * still looking natural on most photos.
         */
        const val RECOMMENDED_STRENGTH = DEFAULT_STRENGTH
    }
}
