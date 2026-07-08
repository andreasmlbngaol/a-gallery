package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A catalog entry paired with its current on-disk state. Produced by
 * `ObserveModelStatusUseCase` and consumed directly by the model list UI.
 *
 * @property spec the immutable catalog metadata.
 * @property installed the imported file, or null when the model is not installed.
 */
data class ModelStatus(
    val spec: AiModelSpec,
    val installed: InstalledModel?,
) {
    /** Whether the model file is present on disk and ready for inference. */
    val isInstalled: Boolean get() = installed != null
}
