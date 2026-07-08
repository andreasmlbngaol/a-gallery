package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.TensorImageUtils
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import java.io.File
import kotlin.math.ceil

/**
 * Does the actual on-device work for background removal: decode -> preprocess ->
 * ONNX inference -> alpha compositing -> write a cutout PNG to the cache.
 *
 * The source image is never modified; the result is a brand-new transparent PNG.
 */
class BackgroundRemovalProcessor(
    private val context: Context,
    private val inferenceEngine: InferenceEngine,
    private val modelPaths: ModelPaths,
) {

    /** Decodes [sourceUri] to a software ARGB_8888 bitmap, or null if unreadable. */
    fun decodeSource(sourceUri: String): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri.toUri())
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > MAX_SOURCE_EDGE) {
                decoder.setTargetSampleSize(ceil(longEdge.toDouble() / MAX_SOURCE_EDGE).toInt())
            }
        }
        if (decoded.config == Bitmap.Config.HARDWARE) {
            decoded.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            decoded
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to decode source image", t)
        null
    }

    /**
     * Runs [spec]'s model (loaded from [modelPath]) on [source] and returns the
     * absolute path of the generated cutout PNG. Throws on inference failure.
     */
    fun removeBackground(source: Bitmap, spec: AiModelSpec, modelPath: String): String {
        val cutout = inferenceEngine.createSession(modelPath).use { session ->
            val input = TensorImageUtils.toInputTensor(source, spec.io)
            val output = session.run(spec.io.inputName, input, spec.io.outputName)
            TensorImageUtils.applyMaskAsAlpha(source, output)
        }
        return try {
            writePng(cutout).absolutePath
        } finally {
            cutout.recycle()
        }
    }

    private fun writePng(bitmap: Bitmap): File {
        val file = modelPaths.newPreviewFile()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private companion object {
        const val TAG = "BackgroundRemoval"
        const val MAX_SOURCE_EDGE = 2048
    }
}
