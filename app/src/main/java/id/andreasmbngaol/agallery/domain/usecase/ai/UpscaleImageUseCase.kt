package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository

/**
 * Runs on-device image upscaling on [sourceUri] using the installed model
 * [modelId], returning an enlarged preview result (not yet saved).
 */
class UpscaleImageUseCase(
    private val repository: ImageUpscaleRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        modelId: AiModelId,
        mode: UpscaleMode,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): UpscaleOutcome = repository.upscale(sourceUri, modelId, mode, onProgress)
}
