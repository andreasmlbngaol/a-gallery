package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceOutcome
import id.andreasmbngaol.agallery.domain.repository.PhotoEnhanceRepository

/**
 * Runs on-device photo enhancement on [sourceUri] using the installed model
 * [modelId] at the given [strength], returning an enhanced preview result (not
 * yet saved).
 */
class EnhancePhotoUseCase(
    private val repository: PhotoEnhanceRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): EnhanceOutcome = repository.enhance(sourceUri, modelId, strength, onProgress)
}
