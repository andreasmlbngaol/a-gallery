package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase

/**
 * Transient state of an in-flight model import. At most one import runs at a
 * time; [modelId] identifies which row is busy and [phase] drives the row's
 * progress label.
 *
 * @property modelId the model currently importing, or null when idle.
 * @property phase the current import stage, or null when idle.
 */
data class AiImportUiState(
    val modelId: AiModelId? = null,
    val phase: ImportPhase? = null,
) {
    /** Whether an import is currently running. */
    val isImporting: Boolean get() = modelId != null
}
