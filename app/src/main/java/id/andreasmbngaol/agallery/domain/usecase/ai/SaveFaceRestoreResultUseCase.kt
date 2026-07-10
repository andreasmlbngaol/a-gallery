package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository

/**
 * Saves a face-restoration preview into the gallery as a new PNG file derived
 * from [sourceDisplayName]. The original image is never modified.
 */
class SaveFaceRestoreResultUseCase(
    private val repository: FaceRestoreRepository,
) {
    suspend operator fun invoke(
        resultPath: String,
        sourceDisplayName: String,
    ): FaceRestoreSaveOutcome = repository.saveResult(resultPath, sourceDisplayName)
}
