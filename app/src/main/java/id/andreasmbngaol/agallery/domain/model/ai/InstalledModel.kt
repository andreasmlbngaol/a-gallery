package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A model file that has been imported and is available on disk for inference.
 *
 * @property id identifier matching the corresponding [AiModelSpec.id].
 * @property absolutePath absolute path to the stored `.onnx` file.
 * @property sizeBytes size of the stored file, in bytes.
 * @property importedAtEpochMs wall-clock import time (epoch millis), for display
 *   and ordering.
 */
data class InstalledModel(
    val id: AiModelId,
    val absolutePath: String,
    val sizeBytes: Long,
    val importedAtEpochMs: Long,
)
