package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.ai.FloatTensor
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.InferenceSession
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.TensorImageUtils
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ModelIoSpec
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Does the actual on-device work for background removal: decode -> preprocess ->
 * ONNX inference -> alpha compositing -> write a cutout PNG to the cache.
 *
 * The source image is never modified; the result is a brand-new transparent PNG.
 *
 * ## Inference resolution
 * Compute cost scales with the number of input pixels, so the model's input
 * resolution is the single biggest speed lever on CPU-bound devices. The user
 * picks it per run via [RemovalQuality] (chosen on the Background Remover
 * screen): ECO runs at 0.5x, BALANCED at 0.75x, HIGH at the model's full native
 * resolution (best quality). If a model has a FIXED input size and rejects the
 * smaller tensor, we transparently retry at the native resolution (see
 * [runSegmentation]). This is intentionally independent of the global
 * PerformanceMode setting, which only governs gallery memory use.
 */
class BackgroundRemovalProcessor(
    private val context: Context,
    private val inferenceEngine: InferenceEngine,
    private val modelPaths: ModelPaths,
) {

    /**
     * Model ids discovered at runtime to have a FIXED input size. We stop trying
     * to downscale their input after the first rejection, so a fixed-size model
     * doesn't pay a wasted failed inference on every run.
     */
    private val fixedSizeModels = java.util.Collections.synchronizedSet(HashSet<String>())

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
     * Runs [spec]'s model (loaded from [modelPath]) on [source] at the requested
     * [quality] and returns the absolute path of the generated cutout PNG.
     * Throws on inference failure.
     */
    fun removeBackground(
        source: Bitmap,
        spec: AiModelSpec,
        modelPath: String,
        quality: RemovalQuality,
    ): String {
        // ISNet/U2-Netp are blocked by linear-mode Resize on XNNPACK: keep on CPU.
        val cutout = inferenceEngine.acquireSession(modelPath, allowXnnpack = false).use { session ->
            val output = runSegmentation(session, source, spec, quality)
            TensorImageUtils.applyMaskAsAlpha(source, output)
        }
        return try {
            writePng(cutout).absolutePath
        } finally {
            cutout.recycle()
        }
    }

    /**
     * Runs the model honoring the requested [quality]. For ECO/BALANCED we feed a
     * smaller input tensor (far less compute). If the model has a fixed input
     * size and throws, we log it and retry once at the native resolution so the
     * feature never breaks — it just doesn't get the speedup on that model.
     */
    private fun runSegmentation(
        session: InferenceSession,
        source: Bitmap,
        spec: AiModelSpec,
        quality: RemovalQuality,
    ): FloatTensor {
        val nativeIo = spec.io
        val scale = quality.scale
        val modelKey = spec.id.value

        if (spec.offersQualityChoice && scale < 1.0f && modelKey !in fixedSizeModels) {
            val scaledIo = nativeIo.scaledBy(scale)
            if (scaledIo.inputWidth != nativeIo.inputWidth || scaledIo.inputHeight != nativeIo.inputHeight) {
                try {
                    val input = TensorImageUtils.toInputTensor(source, scaledIo)
                    val out = session.run(scaledIo.inputName, input, scaledIo.outputName)
                    Log.i(TAG, "Ran at ${scaledIo.inputWidth}x${scaledIo.inputHeight} (quality $quality, scale $scale)")
                    return out
                } catch (t: Throwable) {
                    // Remember this model is fixed-size so future runs skip the
                    // wasted attempt and go straight to native resolution.
                    fixedSizeModels.add(modelKey)
                    Log.w(
                        TAG,
                        "Model '$modelKey' rejected ${scaledIo.inputWidth}x${scaledIo.inputHeight} " +
                            "(fixed-size export); using native " +
                            "${nativeIo.inputWidth}x${nativeIo.inputHeight}. Import the -dynamic " +
                            "model to enable lower-resolution speedups.",
                        t,
                    )
                }
            }
        }

        val input = TensorImageUtils.toInputTensor(source, nativeIo)
        val out = session.run(nativeIo.inputName, input, nativeIo.outputName)
        Log.i(TAG, "Ran at native ${nativeIo.inputWidth}x${nativeIo.inputHeight} (quality $quality)")
        return out
    }

    /**
     * Returns a copy of this spec with the input dimensions scaled by [scale],
     * snapped to a multiple of 32 (friendlier to conv strides) and never below
     * 128px or above the native size.
     */
    private fun ModelIoSpec.scaledBy(scale: Float): ModelIoSpec {
        fun snap(dimension: Int): Int {
            val scaled = (dimension * scale / 32f).roundToInt() * 32
            return scaled.coerceIn(128, dimension)
        }
        return copy(inputWidth = snap(inputWidth), inputHeight = snap(inputHeight))
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
