package id.andreasmbngaol.agallery.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import id.andreasmbngaol.agallery.domain.model.ai.ModelIoSpec
import id.andreasmbngaol.agallery.domain.model.ai.TensorLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/**
 * Converts between [Bitmap]s and the [FloatTensor]s the inference engine speaks:
 * building a normalized input tensor from a photo, and compositing a predicted
 * single-channel mask back onto the original as an alpha channel.
 */
object TensorImageUtils {

    /**
     * Scales [source] to the model's expected input size and packs it into a
     * normalized tensor following [spec] (layout + per-channel mean/std).
     */
    fun toInputTensor(source: Bitmap, spec: ModelIoSpec): FloatTensor {
        val w = spec.inputWidth
        val h = spec.inputHeight
        val scaled = source.scaleTo(w, h)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()

        val area = w * h
        val out = FloatArray(3 * area)
        val mean = spec.mean
        val std = spec.std
        for (i in 0 until area) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val rn = (r - mean[0]) / std[0]
            val gn = (g - mean[1]) / std[1]
            val bn = (b - mean[2]) / std[2]
            when (spec.layout) {
                TensorLayout.NCHW -> {
                    out[i] = rn
                    out[area + i] = gn
                    out[2 * area + i] = bn
                }
                TensorLayout.NHWC -> {
                    out[i * 3] = rn
                    out[i * 3 + 1] = gn
                    out[i * 3 + 2] = bn
                }
            }
        }
        val shape = when (spec.layout) {
            TensorLayout.NCHW -> longArrayOf(1, 3, h.toLong(), w.toLong())
            TensorLayout.NHWC -> longArrayOf(1, h.toLong(), w.toLong(), 3)
        }
        return FloatTensor(out, shape)
    }

    /**
     * Interprets [mask] as a single-channel saliency map (shape `[1,1,H,W]`,
     * `[1,H,W,1]`, or `[H,W]`), min-max normalizes it to 0..1, scales it up to
     * [source]'s dimensions, and returns a new ARGB_8888 bitmap holding
     * [source]'s colors with the mask applied as alpha (transparent background).
     */
    fun applyMaskAsAlpha(source: Bitmap, mask: FloatTensor): Bitmap {
        val (maskW, maskH) = maskDimensions(mask)
        val values = mask.data
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (v in values) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = (max - min).takeIf { it > 1e-6f } ?: 1f

        // Build a small alpha bitmap from the mask, then scale to the source size
        // so we get free bilinear upsampling.
        val maskPixels = IntArray(maskW * maskH)
        for (i in maskPixels.indices) {
            val norm = ((values[i] - min) / range).coerceIn(0f, 1f)
            val a = (norm * 255f).toInt()
            maskPixels[i] = Color.argb(a, a, a, a)
        }
        val maskBitmap = createBitmap(maskW, maskH)
        maskBitmap.setPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)
        val scaledMask = maskBitmap.scaleTo(source.width, source.height)
        if (scaledMask !== maskBitmap) maskBitmap.recycle()

        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val maskScaledPixels = IntArray(width * height)
        scaledMask.getPixels(maskScaledPixels, 0, width, 0, 0, width, height)
        if (scaledMask !== source) scaledMask.recycle()

        for (i in srcPixels.indices) {
            val alpha = maskScaledPixels[i] and 0xFF
            srcPixels[i] = (srcPixels[i] and 0x00FFFFFF) or (alpha shl 24)
        }
        val result = createBitmap(width, height)
        result.setPixels(srcPixels, 0, width, 0, 0, width, height)
        return result
    }

    /** Derives (width, height) of a mask tensor from its shape. */
    private fun maskDimensions(mask: FloatTensor): Pair<Int, Int> {
        val dims = mask.shape.map { it.toInt() }.filter { it > 1 }
        return when {
            dims.size >= 2 -> dims[dims.size - 1] to dims[dims.size - 2]
            dims.size == 1 -> dims[0] to 1
            else -> 1 to 1
        }
    }

    private fun Bitmap.scaleTo(width: Int, height: Int): Bitmap =
        if (this.width == width && this.height == height) this
        else this.scale(width, height)

    /**
     * Reads an RGB image [tensor] produced by an upscaling model back into an
     * ARGB_8888 bitmap. Supports NCHW (`[1,3,H,W]`) and NHWC (`[1,H,W,3]`)
     * outputs whose channel values are in the 0..1 range (the convention used by
     * Real-ESRGAN and SwinIR exports); values are clamped and scaled to 0..255.
     */
    fun imageFromTensor(tensor: FloatTensor): Bitmap {
        val shape = tensor.shape.map { it.toInt() }
        val nchw = shape.size == 4 && shape[1] == 3
        val height: Int
        val width: Int
        if (nchw) {
            height = shape[2]
            width = shape[3]
        } else {
            // NHWC, or a 3-D [H,W,3] fallback.
            height = shape[shape.size - 3]
            width = shape[shape.size - 2]
        }
        val area = width * height
        val data = tensor.data
        val pixels = IntArray(area)
        for (i in 0 until area) {
            val r: Float
            val g: Float
            val b: Float
            if (nchw) {
                r = data[i]
                g = data[area + i]
                b = data[2 * area + i]
            } else {
                r = data[i * 3]
                g = data[i * 3 + 1]
                b = data[i * 3 + 2]
            }
            val ri = (r.coerceIn(0f, 1f) * 255f).roundToInt()
            val gi = (g.coerceIn(0f, 1f) * 255f).roundToInt()
            val bi = (b.coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

}
