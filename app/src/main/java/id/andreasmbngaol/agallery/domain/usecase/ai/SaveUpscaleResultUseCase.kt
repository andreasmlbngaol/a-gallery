package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.UpscaleSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository

/**
 * Saves an upscaled preview into the gallery as a new PNG file derived from
 * [sourceDisplayName]. The original image is never modified.
 */
class SaveUpscaleResultUseCase(
    private val repository: ImageUpscaleRepository,
) {
    suspend operator fun invoke(
        resultPath: String,
        sourceDisplayName: String,
    ): UpscaleSaveOutcome = repository.saveResult(resultPath, sourceDisplayName)
}
