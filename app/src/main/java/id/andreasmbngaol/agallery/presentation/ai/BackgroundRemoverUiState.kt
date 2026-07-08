package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the Background Remover screen.
 *
 * @property sourceUri the image being edited.
 * @property sourceDisplayName the original file name, used to derive the saved name.
 * @property installedModels installed models the user can run, in display order.
 * @property selectedModelId the model chosen to run, or null to use the default.
 * @property resultPath the transparent preview PNG path once produced, else null.
 * @property processing whether inference is currently running.
 * @property saving whether the result is currently being saved to the gallery.
 * @property componentStyleChosen chosen component style, or null while loading.
 * @property edgeEffectMode chosen edge-effect mode, or null while loading.
 * @property checkingModels whether the installed-model check is still running, so
 *   the screen can show a spinner instead of briefly flashing the empty state.
 * @property processingElapsedSeconds seconds elapsed in the current run, shown live.
 * @property processingUsedMemoryBytes process memory (PSS) sampled during the run.
 */
data class BackgroundRemoverUiState(
    val sourceUri: String = "",
    val sourceDisplayName: String = "",
    val installedModels: List<AiModelSpec> = emptyList(),
    val selectedModelId: AiModelId? = null,
    val resultPath: String? = null,
    val processing: Boolean = false,
    val saving: Boolean = false,
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
    val checkingModels: Boolean = true,
    val processingElapsedSeconds: Int = 0,
    val processingUsedMemoryBytes: Long = 0L,
) {
    /** Whether at least one model is installed and usable. */
    val hasModel: Boolean get() = installedModels.isNotEmpty()
}
