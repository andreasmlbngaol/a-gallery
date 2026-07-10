package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.FaceDetectionResult
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository

/**
 * Runs on-device face detection on [sourceUri] so the UI can preview which faces
 * will be restored (drawn as bounding boxes over the original). Detection is
 * cheap and independent of the chosen model, so it runs as soon as the screen
 * opens. Returns [FaceDetectionResult.EMPTY] when the image is unreadable.
 */
class DetectFacesUseCase(
    private val repository: FaceRestoreRepository,
) {
    suspend operator fun invoke(sourceUri: String): FaceDetectionResult =
        repository.detectFaces(sourceUri)
}
