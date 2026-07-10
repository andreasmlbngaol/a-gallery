package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.FaceDetectionResult
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreOutcome
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreSaveOutcome

/**
 * Contract for the Face Restore feature. The implementation (image decoding,
 * on-device face detection, per-face ONNX restoration, paste-back, MediaStore
 * save) lives in the data layer. All work is on-device.
 */
interface FaceRestoreRepository {
    /**
     * Detects faces in the image at [sourceUri] (on-device, model-independent)
     * so the UI can show which faces will be restored. Returns
     * [FaceDetectionResult.EMPTY] when the image can't be read.
     */
    suspend fun detectFaces(sourceUri: String): FaceDetectionResult

    /**
     * Restores every detected face in the image at [sourceUri] using the
     * installed model [modelId], writing the enhanced result to a cache file for
     * preview. [strength] (0..1) blends the restored face over the original: a
     * higher value is sharper but less natural. Does not touch the gallery.
     */
    suspend fun restore(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): FaceRestoreOutcome

    /**
     * Persists a previously produced preview at [resultPath] into the gallery as
     * a new PNG file derived from [sourceDisplayName] (the original is untouched).
     */
    suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): FaceRestoreSaveOutcome
}
