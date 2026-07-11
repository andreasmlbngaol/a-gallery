package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository

/**
 * Runs on-device image upscaling on [sourceUri] using the installed model
 * [modelId], returning an enlarged preview result (not yet saved). [strength] (0..1) blends
 * the AI result over a plain resize of the source (1 = full super-resolution,
 * lower = more natural).
 */
class UpscaleImageUseCase(
    private val repository: ImageUpscaleRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        modelId: AiModelId,
        mode: UpscaleMode,
        strength: Float = 1f,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): UpscaleOutcome = repository.upscale(sourceUri, modelId, mode, strength, onProgress = onProgress)
}
