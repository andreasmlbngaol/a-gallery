package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.EnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.PhotoEnhanceRepository

/**
 * Saves an enhanced preview into the gallery as a new PNG file derived from
 * [sourceDisplayName]. The original image is never modified.
 */
class SavePhotoEnhanceResultUseCase(
    private val repository: PhotoEnhanceRepository,
) {
    suspend operator fun invoke(
        resultPath: String,
        sourceDisplayName: String,
    ): EnhanceSaveOutcome = repository.saveResult(resultPath, sourceDisplayName)
}
