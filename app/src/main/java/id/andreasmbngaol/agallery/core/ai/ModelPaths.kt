package id.andreasmbngaol.agallery.core.ai

import android.content.Context
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import java.io.File

/**
 * Resolves where AI artifacts live in app-private storage. Model files sit under
 * `filesDir/ai_models/<id>.onnx`; transient result previews sit under
 * `cacheDir/ai_bg/` (cutouts) and `cacheDir/ai_upscale/` (upscaled images).
 * Nothing here is world-readable and nothing is fetched over the network.
 */
class ModelPaths(
    private val context: Context,
) {
    /** The directory that holds imported `.onnx` files (created on demand). */
    fun modelsDir(): File = File(context.filesDir, MODELS_DIR).apply { mkdirs() }

    /** The on-disk file (which may not exist yet) for [id]. */
    fun modelFile(id: AiModelId): File = File(modelsDir(), "${id.value}.$MODEL_EXT")

    /** The cache directory for transient result previews (created on demand). */
    fun previewCacheDir(): File = File(context.cacheDir, PREVIEW_DIR).apply { mkdirs() }

    /** A fresh, unique cache file for a background-removal preview PNG. */
    fun newPreviewFile(): File =
        File(previewCacheDir(), "nobg_${System.currentTimeMillis()}.png")

    /** The cache directory for transient upscale previews (created on demand). */
    fun upscaleCacheDir(): File = File(context.cacheDir, UPSCALE_DIR).apply { mkdirs() }

    /** A fresh, unique cache file for an upscaled-image preview PNG. */
    fun newUpscaleFile(): File =
        File(upscaleCacheDir(), "upscaled_${System.currentTimeMillis()}.png")

    private companion object {
        const val MODELS_DIR = "ai_models"
        const val PREVIEW_DIR = "ai_bg"
        const val UPSCALE_DIR = "ai_upscale"
        const val MODEL_EXT = "onnx"
    }
}
