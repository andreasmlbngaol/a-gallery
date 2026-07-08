package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundSaveOutcome

/**
 * Contract for the Background Remover feature. The implementation (image
 * decoding, ONNX inference, mask compositing, MediaStore save) lives in the data
 * layer. All work is on-device.
 */
interface BackgroundRemovalRepository {
    /**
     * Removes the background from the image at [sourceUri] using the installed
     * model [modelId], writing the transparent result to a cache file for
     * preview. Does not touch the gallery.
     */
    suspend fun removeBackground(
        sourceUri: String,
        modelId: AiModelId,
    ): BackgroundRemovalOutcome

    /**
     * Persists a previously produced preview at [resultPath] into the gallery as
     * a new PNG file derived from [sourceDisplayName] (the original is untouched).
     */
    suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): BackgroundSaveOutcome
}
