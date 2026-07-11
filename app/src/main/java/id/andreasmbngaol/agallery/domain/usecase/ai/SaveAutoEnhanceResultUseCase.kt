package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.AutoEnhanceRepository

/**
 * Saves an Auto Enhance preview into the gallery as a new PNG file (under
 * Pictures/AGallery Auto Enhanced) derived from [sourceDisplayName]. The
 * original image is never modified.
 */
class SaveAutoEnhanceResultUseCase(
    private val repository: AutoEnhanceRepository,
) {
    suspend operator fun invoke(
        resultPath: String,
        sourceDisplayName: String,
    ): AutoEnhanceSaveOutcome = repository.saveResult(resultPath, sourceDisplayName)
}
