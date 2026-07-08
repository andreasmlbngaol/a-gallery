package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.repository.BackgroundRemovalRepository

/**
 * Runs on-device background removal on [sourceUri] using the installed model
 * [modelId] at the requested [quality], returning a transparent preview result
 * (not yet saved).
 */
class RemoveBackgroundUseCase(
    private val repository: BackgroundRemovalRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        modelId: AiModelId,
        quality: RemovalQuality,
    ): BackgroundRemovalOutcome = repository.removeBackground(sourceUri, modelId, quality)
}
