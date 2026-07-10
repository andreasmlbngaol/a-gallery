package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceSaveOutcome

/**
 * Contract for the Photo Enhance feature. The implementation (image decoding,
 * tiled ONNX restoration, strength blend, MediaStore save) lives in the data
 * layer. All work is on-device.
 */
interface PhotoEnhanceRepository {
    /**
     * Enhances the image at [sourceUri] using the installed model [modelId],
     * writing the restored result to a cache file for preview. [strength] (0..1)
     * blends the enhanced result over the original: higher is a stronger clean-up,
     * lower keeps more of the original texture. Keeps the original resolution.
     * Does not touch the gallery.
     */
    suspend fun enhance(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): EnhanceOutcome

    /**
     * Persists a previously produced preview at [resultPath] into the gallery as
     * a new PNG file derived from [sourceDisplayName] (the original is untouched).
     */
    suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): EnhanceSaveOutcome
}
