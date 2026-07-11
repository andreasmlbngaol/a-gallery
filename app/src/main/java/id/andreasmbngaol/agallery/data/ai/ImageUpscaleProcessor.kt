package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.InferenceSession
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.TensorImageUtils
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.scale

/**
 * Does the actual on-device work for image upscaling: decode -> tiled ONNX
 * super-resolution -> reassemble -> write an enlarged PNG to the cache. The
 * source image is never modified; the result is a brand-new file.
 *
 * ## Why tiling
 * The upscale models (Real-ESRGAN, SwinIR) are exported with a FIXED, square
 * input (e.g. 128x128) and a fixed [ModelIoSpec.scaleFactor] (4x). To enlarge a
 * whole photo we slide that fixed window across the source in overlapping
 * [TILE] steps, run each tile, and stitch the (tile*scale) outputs into the
 * final canvas. Overlap of [OVERLAP] source pixels per side is fed as context
 * and then discarded on stitch, which removes visible seams between tiles.
 *
 * ## Memory
 * The source long edge is capped at [MAX_SOURCE_EDGE] so the 4x output ARGB
 * bitmap stays bounded (<= 4096px edge, ~64MB). Per-tile tensors are tiny and
 * released immediately; only the source and the growing output canvas persist.
 */
class ImageUpscaleProcessor(
    private val context: Context,
    private val inferenceEngine: InferenceEngine,
    private val modelPaths: ModelPaths,
) {

    /**
     * Decodes [sourceUri] to a software ARGB_8888 bitmap plus its ORIGINAL
     * (pre-downsample) dimensions, or null if unreadable. The long edge is
     * capped so tiling/memory stay bounded; [UpscaleMode.FULL] uses a larger cap
     * to allow a genuine full-resolution 4x. The original size is reported so
     * the caller can resize the result back down for the size-preserving modes.
     */
    fun decodeSource(sourceUri: String, mode: UpscaleMode): DecodedSource? = try {
        var originalWidth = 0
        var originalHeight = 0
        val cap = if (mode == UpscaleMode.FULL) FULL_MAX_SOURCE_EDGE else MAX_SOURCE_EDGE
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri.toUri())
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            originalWidth = info.size.width
            originalHeight = info.size.height
            val longEdge = maxOf(originalWidth, originalHeight)
            if (longEdge > cap) {
                decoder.setTargetSampleSize(ceil(longEdge.toDouble() / cap).toInt())
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
     * Upscales [source] with [spec]'s model (loaded from [modelPath]) and returns
     * the absolute path of the generated PNG. Throws on inference failure.
     */
    suspend fun upscale(
        source: Bitmap,
        spec: AiModelSpec,
        modelPath: String,
        mode: UpscaleMode,
        originalWidth: Int,
        originalHeight: Int,
        strength: Float = 1f,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): String {
        val upscaled = inferenceEngine.acquireSession(modelPath, allowXnnpack = spec.xnnpackEligible).use { session ->
            runTiled(session, source, spec, onProgress)
        }
        // The models are RGB-only (3 channels), so the upscaled canvas is fully
        // opaque. If the source had transparency (e.g. a PNG whose background
        // was removed), reinstate it so transparent areas don't turn black.
        applyAlpha(source, upscaled)
        // Size-preserving modes shrink the sharpened result back to the original
        // resolution; enlarging modes keep the model's full output.
        val result = resizeForMode(upscaled, mode, originalWidth, originalHeight)
        // Real-ESRGAN has no native intensity control, so "strength" is applied
        // here as an opacity blend of the AI result over a plain (bilinear)
        // resize of the source at the SAME target size. strength=1 keeps the
        // full super-resolution; lower values pull the aggressive, over-sharpened
        // texture back toward a natural resize.
        if (strength < 0.999f) blendTowardPlain(result, source, strength)
        return try {
            writePng(result).absolutePath
        } finally {
            if (result != upscaled) result.recycle()
            upscaled.recycle()
        }
    }

    /**
     * Blends the AI [target] toward a plain, non-AI reference so the upscale
     * gets a real "strength" knob (the model itself has none). The reference is
     * a bilinear resize of [source] to [target]'s size; each pixel becomes
     * strength*AI + (1-strength)*plain on the RGB channels, keeping [target]'s
     * alpha. Mutates [target] in place, row by row, to avoid a second full-size
     * allocation. No-op-ish at strength≈1 (guarded by the caller).
     */
    private fun blendTowardPlain(target: Bitmap, source: Bitmap, strength: Float) {
        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) return
        val a = strength.coerceIn(0f, 1f)
        val inv = 1f - a
        val plain = source.scale(w, h)
        try {
            val aiRow = IntArray(w)
            val plainRow = IntArray(w)
            for (y in 0 until h) {
                target.getPixels(aiRow, 0, w, 0, y, w, 1)
                plain.getPixels(plainRow, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val ai = aiRow[x]
                    val pl = plainRow[x]
                    val alpha = ai and 0xFF000000.toInt()
                    val r = ((ai ushr 16 and 0xFF) * a + (pl ushr 16 and 0xFF) * inv)
                        .roundToInt().coerceIn(0, 255)
                    val g = ((ai ushr 8 and 0xFF) * a + (pl ushr 8 and 0xFF) * inv)
                        .roundToInt().coerceIn(0, 255)
                    val b = ((ai and 0xFF) * a + (pl and 0xFF) * inv)
                        .roundToInt().coerceIn(0, 255)
                    aiRow[x] = alpha or (r shl 16) or (g shl 8) or b
                }
                target.setPixels(aiRow, 0, w, 0, y, w, 1)
            }
        } finally {
            if (plain != source) plain.recycle()
        }
    }

    /**
     * Applies [mode]'s output-size policy to the freshly [upscaled] bitmap:
     * - [UpscaleMode.ENLARGE] / [UpscaleMode.FULL]: keep the enlarged result.
     * - [UpscaleMode.ORIGINAL_SIZE]: shrink back to the source's original size.
     * - [UpscaleMode.AUTO]: enlarge small sources, but keep large ones at their
     *   original size (so a big photo just gets cleaner, not enormous).
     * Returns [upscaled] unchanged when no resize is needed.
     */
    private fun resizeForMode(
        upscaled: Bitmap,
        mode: UpscaleMode,
        originalWidth: Int,
        originalHeight: Int,
    ): Bitmap {
        val keepOriginalSize = when (mode) {
            UpscaleMode.ORIGINAL_SIZE -> true
            UpscaleMode.AUTO -> maxOf(originalWidth, originalHeight) > MAX_SOURCE_EDGE
            UpscaleMode.ENLARGE, UpscaleMode.FULL -> false
        }
        if (!keepOriginalSize) return upscaled
        if (originalWidth <= 0 || originalHeight <= 0) return upscaled
        if (originalWidth == upscaled.width && originalHeight == upscaled.height) return upscaled
        return upscaled.scale(originalWidth, originalHeight)
    }

    /**
     * Copies [source]'s alpha channel onto the opaque [target] (the upscaled
     * RGB), so transparency survives super-resolution. The alpha mask is scaled
     * to [target]'s size with bilinear filtering (the model never sees alpha).
     * No-op for fully opaque sources.
     */
    private fun applyAlpha(source: Bitmap, target: Bitmap) {
        if (!source.hasAlpha()) return
        val w = target.width
        val h = target.height
        val scaledAlpha = source.scale(w, h)
        try {
            target.setHasAlpha(true)
            val rgbRow = IntArray(w)
            val alphaRow = IntArray(w)
            for (y in 0 until h) {
                target.getPixels(rgbRow, 0, w, 0, y, w, 1)
                scaledAlpha.getPixels(alphaRow, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    rgbRow[x] = (alphaRow[x] and 0xFF000000.toInt()) or (rgbRow[x] and 0x00FFFFFF)
                }
                target.setPixels(rgbRow, 0, w, 0, y, w, 1)
            }
        } finally {
            if (scaledAlpha != source) scaledAlpha.recycle()
        }
    }

    /**
     * Slides the model's fixed square input over [source] in overlapping steps,
     * runs each tile, and stitches the central (non-overlap) region of every
     * (tile*scale) output into the final [scale]x canvas.
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
        // Context fed around each tile then discarded; keeps seams invisible.
        val overlap = (tile / OVERLAP_DIVISOR).coerceAtLeast(1)
        val step = (tile - overlap * 2).coerceAtLeast(1)

        val srcW = source.width
        val srcH = source.height
        val output = createBitmap(srcW * scale, srcH * scale)

        // Total tiles = ceil(w/step) * ceil(h/step). Reported up front (0/total)
        // so the loading dialog can show "done / total" and estimate the wait
        // from the average time of the tiles finished so far.
        val tilesX = ceil(srcW.toDouble() / step).toInt().coerceAtLeast(1)
        val tilesY = ceil(srcH.toDouble() / step).toInt().coerceAtLeast(1)
        val totalTiles = tilesX * tilesY
        var completed = 0
        onProgress(0, totalTiles)

        var oy = 0
        while (oy < srcH) {
            var ox = 0
            while (ox < srcW) {
                // Stop promptly if this run was cancelled/superseded: the loop is
                // otherwise uninterruptible blocking work, which let a cancelled
                // run keep emitting progress and clash with the next run.
                currentCoroutineContext().ensureActive()
                // Input window top-left, shifted back by the overlap and
                // edge-clamped so tiles at the border still fill the tensor.
                val tileBmp = extractTile(source, ox - overlap, oy - overlap, tile)
                val inputTensor = TensorImageUtils.toInputTensor(tileBmp, io)
                tileBmp.recycle()

                val outTensor = session.run(io.inputName, inputTensor, io.outputName)
                val outTile = TensorImageUtils.imageFromTensor(outTensor)

                // Copy only the valid center (the content this tile owns),
                // discarding the upscaled overlap borders.
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
     * Extracts a [size]x[size] ARGB tile from [source] whose top-left is
     * ([left],[top]) in source space, replicating edge pixels for coordinates
     * that fall outside the image (so border tiles are fully populated).
     */
    private fun extractTile(source: Bitmap, left: Int, top: Int, size: Int): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val src = IntArray(srcW * srcH)
        source.getPixels(src, 0, srcW, 0, 0, srcW, srcH)
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
        val file = modelPaths.newUpscaleFile()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private companion object {
        const val TAG = "ImageUpscale"

        /**
         * Max source long edge fed to the model. The 4x output stays <= 4096px
         * (~64MB ARGB), which is safe on mid-range devices. Larger sources are
         * downsampled at decode time; the result is still a genuine upscale of
         * that (very large) input.
         */
        const val MAX_SOURCE_EDGE = 1024

        /**
         * Larger cap used only by [UpscaleMode.FULL] so a genuine 4x is possible
         * for big photos (e.g. 1920 -> 7680). Heavier on RAM, hence opt-in.
         */
        const val FULL_MAX_SOURCE_EDGE = 2048

        /** Overlap = tile / this, per side (e.g. 128/16 = 8px). */
        const val OVERLAP_DIVISOR = 16
    }
}

/**
 * A decoded source bitmap plus the ORIGINAL image dimensions (before any
 * downsampling at decode time), used to resize results back for the
 * size-preserving upscale modes.
 */
data class DecodedSource(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
)
