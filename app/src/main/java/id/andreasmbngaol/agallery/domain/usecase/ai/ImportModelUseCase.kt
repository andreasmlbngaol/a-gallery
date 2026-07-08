package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ImportOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository

/**
 * Imports a user-picked `.onnx` file as [spec], forwarding progress via
 * [onPhase]. The file is copied into app storage and validated before it counts
 * as installed.
 */
class ImportModelUseCase(
    private val repository: AiModelRepository,
) {
    suspend operator fun invoke(
        spec: AiModelSpec,
        sourceUri: String,
        onPhase: (ImportPhase) -> Unit = {},
    ): ImportOutcome = repository.import(spec, sourceUri, onPhase)
}
