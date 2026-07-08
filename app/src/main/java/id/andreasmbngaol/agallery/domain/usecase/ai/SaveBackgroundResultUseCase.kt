package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.BackgroundSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.BackgroundRemovalRepository

/**
 * Saves a background-removal preview into the gallery as a new PNG file derived
 * from [sourceDisplayName]. The original image is never modified.
 */
class SaveBackgroundResultUseCase(
    private val repository: BackgroundRemovalRepository,
) {
    suspend operator fun invoke(
        resultPath: String,
        sourceDisplayName: String,
    ): BackgroundSaveOutcome = repository.saveResult(resultPath, sourceDisplayName)
}
