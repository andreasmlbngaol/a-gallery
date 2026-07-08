package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability

/**
 * A single row in the AI models list: the catalog [spec] joined with its current
 * install/import state, ready for [AiModelCard] to render without further logic.
 *
 * @property spec the catalog metadata for the model.
 * @property isInstalled whether the model file is present on disk.
 * @property installedSizeBytes the on-disk size, or null when not installed.
 * @property isImporting whether this specific model is currently importing.
 * @property importPhase the current import stage for this row, or null.
 * @property suitability how well this device can run the model, or null while
 *   the device benchmark is still running.
 */
data class AiModelRow(
    val spec: AiModelSpec,
    val isInstalled: Boolean,
    val installedSizeBytes: Long?,
    val isImporting: Boolean,
    val importPhase: ImportPhase?,
    val suitability: ModelSuitability? = null,
)
