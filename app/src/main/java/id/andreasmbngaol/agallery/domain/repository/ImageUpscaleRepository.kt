package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleSaveOutcome

/**
 * Contract for the Image Upscaler feature. The implementation (image decoding,
 * tiled ONNX inference, image assembly, MediaStore save) lives in the data
 * layer. All work is on-device.
 */
interface ImageUpscaleRepository {
    /**
     * Upscales the image at [sourceUri] using the installed model [modelId],
     * writing the enlarged result to a cache file for preview. [strength] (0..1)
     * blends the AI result over a plain resize of the source (1 = full AI,
     * lower = more natural); defaults to full strength. Does not touch the
     * gallery.
     */
    suspend fun upscale(
        sourceUri: String,
        modelId: AiModelId,
        mode: UpscaleMode,
        strength: Float = 1f,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): UpscaleOutcome

    /**
     * Persists a previously produced preview at [resultPath] into the gallery as
     * a new PNG file derived from [sourceDisplayName] (the original is untouched).
     */
    suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): UpscaleSaveOutcome
}
