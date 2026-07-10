package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreOutcome
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository

/**
 * Runs on-device face restoration on [sourceUri] using the installed model
 * [modelId] at the given [strength], returning an enhanced preview result (not
 * yet saved).
 */
class RestoreFacesUseCase(
    private val repository: FaceRestoreRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): FaceRestoreOutcome = repository.restore(sourceUri, modelId, strength, onProgress)
}
