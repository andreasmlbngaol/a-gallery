package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.InferenceSession
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.TensorImageUtils
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Does the actual on-device work for photo enhancement: decode -> tiled ONNX
 * restoration -> reassemble -> strength-blend over the original -> write a PNG
 * to the cache. The source image is never modified; the result is a brand-new
 * file at the SAME resolution as the original (Enhance restores detail, it does
 * not enlarge).
 *
 * ## Model
 * The Enhance models are SCUNet (Swin-Conv-UNet) blind denoisers. They take RGB
 * in 0..1 with no mean/std shift (mean 0 / std 1), are fully-convolutional, and
 * output a cleaned RGB image the SAME size as the input ([ModelIoSpec.scaleFactor]
 * = 1). They remove sensor noise, JPEG artefacts and mild blur while keeping the
 * geometry intact.
 *
 * ## Why tiling
 * To bound memory and to feed the network a size it is happy with (a multiple of
 * its downsampling factor), we slide a FIXED square window ([ModelIoSpec.inputWidth])
 * across the source in overlapping [OVERLAP] steps, run each tile, and stitch the
 * central (non-overlap) region of every output back into the canvas. Overlap is
 * fed as context then discarded, which removes visible seams between tiles.
 *
 * ## Strength
 * The freshly restored canvas is alpha-blended over the (capped) original by the
 * caller's strength: 1.0 is the full clean-up, lower values keep more of the
 * original's natural texture so the result never looks over-smoothed / plastic.
 *
 * ## Memory
 * The source long edge is capped at [MAX_SOURCE_EDGE] for the tiled pass; the
 * ORIGINAL dimensions are remembered so the blended result is resized back to
 * the true original size before it is written (so Enhance is always
 * size-preserving, even for very large photos).
 */
class PhotoEnhanceProcessor(
    private val context: Context,
    private val inferenceEngine: InferenceEngine,
    private val modelPaths: ModelPaths,
) {

    /**
     * Decodes [sourceUri] to a software ARGB_8888 bitmap plus its ORIGINAL
     * (pre-downsample) dimensions, or null if unreadable. The long edge is
     * capped so tiling/memory stay bounded; the original size is reported so the
     * caller can resize the size-preserving result back up.
     */
    fun decodeSource(sourceUri: String): DecodedSource? = try {
        var originalWidth = 0
        var originalHeight = 0
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri.toUri())
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            originalWidth = info.size.width
            originalHeight = info.size.height
            val longEdge = maxOf(originalWidth, originalHeight)
            if (longEdge > MAX_SOURCE_EDGE) {
                decoder.setTargetSampleSize(ceil(longEdge.toDouble() / MAX_SOURCE_EDGE).toInt())
            }
        }
        val bitmap = if (decoded.config == Bitmap.Config.HARDWARE) {
            decoded.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            decoded
        }
        DecodedSource(bitmap, originalWidth, originalHeight)
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to decode source image", t)
        null
    }

    /**
     * Enhances [source] with [spec]'s model (loaded from [modelPath]) at the
     * given [strength] and returns the absolute path of the generated PNG. The
     * result is resized back to ([originalWidth] x [originalHeight]). Throws on
     * inference failure.
     */
    suspend fun enhance(
        source: Bitmap,
        spec: AiModelSpec,
        modelPath: String,
        strength: Float,
        originalWidth: Int,
        originalHeight: Int,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): String {
        // SCUNet is transformer-heavy (~2% Conv): XNNPACK gives no gain, stay on CPU.
        val enhanced = inferenceEngine.acquireSession(modelPath, allowXnnpack = false).use { session ->
            runTiled(session, source, spec, onProgress)
        }
        try {
            // Blend the restored canvas over the original by strength, then keep
            // the source's alpha (SCUNet is RGB-only, so its canvas is opaque).
            blendStrength(source, enhanced, strength)
            applyAlpha(source, enhanced)
            val result = resizeToOriginal(enhanced, originalWidth, originalHeight)
            return try {
                writePng(result).absolutePath
            } finally {
                if (result != enhanced) result.recycle()
            }
        } finally {
            enhanced.recycle()
        }
    }

    /**
     * Slides the model's fixed square input over [source] in overlapping steps,
     * runs each tile, and stitches the central (non-overlap) region of every
     * output back into a same-size canvas (scaleFactor is 1 for restoration).
     */
    private suspend fun runTiled(
        session: InferenceSession,
        source: Bitmap,
        spec: AiModelSpec,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Bitmap {
        val io = spec.io
        val tile = io.inputWidth
        val scale = io.scaleFactor.coerceAtLeast(1)
        val overlap = (tile / OVERLAP_DIVISOR).coerceAtLeast(1)
        val step = (tile - overlap * 2).coerceAtLeast(1)

        val srcW = source.width
        val srcH = source.height
        val output = createBitmap(srcW * scale, srcH * scale)

        val tilesX = ceil(srcW.toDouble() / step).toInt().coerceAtLeast(1)
        val tilesY = ceil(srcH.toDouble() / step).toInt().coerceAtLeast(1)
        val totalTiles = tilesX * tilesY
        var completed = 0
        onProgress(0, totalTiles)

        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        var oy = 0
        while (oy < srcH) {
            var ox = 0
            while (ox < srcW) {
                // The per-tile loop is otherwise uninterruptible blocking work,
                // so bail promptly if this run was cancelled/superseded.
                currentCoroutineContext().ensureActive()
                val tileBmp = extractTile(srcPixels, srcW, srcH, ox - overlap, oy - overlap, tile)
                val inputTensor = TensorImageUtils.toInputTensor(tileBmp, io)
                tileBmp.recycle()

                val outTensor = session.run(io.inputName, inputTensor, io.outputName)
                val outTile = TensorImageUtils.imageFromTensor(outTensor)

                val contentW = min(step, srcW - ox)
                val contentH = min(step, srcH - oy)
                copyRegion(
                    from = outTile,
                    fromX = overlap * scale,
                    fromY = overlap * scale,
                    width = contentW * scale,
                    height = contentH * scale,
                    to = output,
                    toX = ox * scale,
                    toY = oy * scale,
                )
                outTile.recycle()
                completed++
                onProgress(completed, totalTiles)
                ox += step
            }
            oy += step
        }
        return output
    }

    /**
     * Alpha-blends the restored [enhanced] canvas over the [source] in place
     * (writing into [enhanced]) by [strength]: `out = enhanced*s + source*(1-s)`.
     * A strength of 1 keeps the full clean-up; lower values preserve the
     * original's natural texture. No-op at full strength. Both bitmaps are the
     * same size (scaleFactor 1).
     */
    private fun blendStrength(source: Bitmap, enhanced: Bitmap, strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        if (s >= 0.999f) return
        val w = min(source.width, enhanced.width)
        val h = min(source.height, enhanced.height)
        val inv = 1f - s
        val srcRow = IntArray(w)
        val outRow = IntArray(w)
        for (y in 0 until h) {
            source.getPixels(srcRow, 0, w, 0, y, w, 1)
            enhanced.getPixels(outRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val o = srcRow[x]
                val e = outRow[x]
                val nr = ((e ushr 16 and 0xFF) * s + (o ushr 16 and 0xFF) * inv).roundToInt()
                val ng = ((e ushr 8 and 0xFF) * s + (o ushr 8 and 0xFF) * inv).roundToInt()
                val nb = ((e and 0xFF) * s + (o and 0xFF) * inv).roundToInt()
                outRow[x] = (e and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
            enhanced.setPixels(outRow, 0, w, 0, y, w, 1)
        }
    }

    /**
     * Copies [source]'s alpha channel onto the opaque [target], so any
     * transparency in the source survives enhancement. No-op for opaque sources.
     */
    private fun applyAlpha(source: Bitmap, target: Bitmap) {
        if (!source.hasAlpha()) return
        val w = min(source.width, target.width)
        val h = min(source.height, target.height)
        target.setHasAlpha(true)
        val rgbRow = IntArray(w)
        val alphaRow = IntArray(w)
        for (y in 0 until h) {
            target.getPixels(rgbRow, 0, w, 0, y, w, 1)
            source.getPixels(alphaRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                rgbRow[x] = (alphaRow[x] and 0xFF000000.toInt()) or (rgbRow[x] and 0x00FFFFFF)
            }
            target.setPixels(rgbRow, 0, w, 0, y, w, 1)
        }
    }

    /** Resizes [enhanced] back to the original size when the tiled pass was downsampled. */
    private fun resizeToOriginal(enhanced: Bitmap, originalWidth: Int, originalHeight: Int): Bitmap {
        if (originalWidth <= 0 || originalHeight <= 0) return enhanced
        if (originalWidth == enhanced.width && originalHeight == enhanced.height) return enhanced
        return enhanced.scale(originalWidth, originalHeight)
    }

    /**
     * Extracts a [size]x[size] ARGB tile from the source pixel buffer whose
     * top-left is ([left],[top]) in source space, replicating edge pixels for
     * coordinates outside the image (so border tiles are fully populated).
     */
    private fun extractTile(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        left: Int,
        top: Int,
        size: Int,
    ): Bitmap {
        val out = IntArray(size * size)
        for (ty in 0 until size) {
            val sy = (top + ty).coerceIn(0, srcH - 1)
            val rowBase = sy * srcW
            val outRow = ty * size
            for (tx in 0 until size) {
                val sx = (left + tx).coerceIn(0, srcW - 1)
                out[outRow + tx] = src[rowBase + sx]
            }
        }
        val bmp = createBitmap(size, size)
        bmp.setPixels(out, 0, size, 0, 0, size, size)
        return bmp
    }

    /** Copies a [width]x[height] block from [from]@([fromX],[fromY]) into [to]@([toX],[toY]), clamped to bounds. */
    private fun copyRegion(
        from: Bitmap,
        fromX: Int,
        fromY: Int,
        width: Int,
        height: Int,
        to: Bitmap,
        toX: Int,
        toY: Int,
    ) {
        val w = minOf(width, from.width - fromX, to.width - toX)
        val h = minOf(height, from.height - fromY, to.height - toY)
        if (w <= 0 || h <= 0) return
        val buffer = IntArray(w * h)
        from.getPixels(buffer, 0, w, fromX, fromY, w, h)
        to.setPixels(buffer, 0, w, toX, toY, w, h)
    }

    private fun writePng(bitmap: Bitmap): File {
        val file = modelPaths.newEnhanceFile()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private companion object {
        const val TAG = "PhotoEnhance"

        /**
         * Max source long edge fed to the tiled pass. Enhance keeps the original
         * resolution, so larger photos are processed at this cap and the result
         * is scaled back up to the true size; caps peak memory on mid-range
         * devices while still cleaning the whole frame.
         */
        const val MAX_SOURCE_EDGE = 2048

        /** Overlap = tile / this, per side (e.g. 256/16 = 16px). */
        const val OVERLAP_DIVISOR = 16
    }
}
