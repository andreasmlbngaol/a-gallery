package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import id.andreasmbngaol.agallery.core.ai.FloatTensor
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.InferenceSession
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.TensorImageUtils
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.FaceBox
import id.andreasmbngaol.agallery.domain.model.ai.FaceDetectionResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Does the actual on-device work for face restoration: decode -> detect faces
 * (ML Kit, bundled/offline) -> per-face crop -> GPEN ONNX restoration -> feathered
 * paste-back -> write a PNG to the cache. The source image is never modified and
 * the non-face areas of the photo are copied through untouched; only the face
 * regions are enhanced.
 *
 * ## Face detection
 * Detection uses ML Kit's BUNDLED face detector (already the same on-device,
 * no-INTERNET stack the app uses for QR/barcode), so no extra model file needs
 * to be imported. Every detected face is restored; a photo with no faces is
 * reported so the UI can tell the user there was nothing to do.
 *
 * ## GPEN I/O
 * The GPEN-BFR models take a FIXED square face crop (256 or 512, per
 * [AiModelSpec.io]) as NCHW RGB normalized to [-1, 1] (mean 0.5 / std 0.5) and
 * output the restored face in the same [-1, 1] range. [TensorImageUtils] already
 * produces the [-1, 1] input from that mean/std; the output is de-normalized here
 * (the shared upscale reader assumes a 0..1 range, which would clip GPEN).
 *
 * ## Strength
 * The restored face is alpha-blended over the original crop by the caller's
 * strength (see the repository): high is sharper but can look plastic / drift
 * from the real identity, low is more natural. A soft edge feather hides the
 * crop boundary so the paste-back is seamless.
 */
class FaceRestoreProcessor(
    private val context: Context,
    private val inferenceEngine: InferenceEngine,
    private val modelPaths: ModelPaths,
) {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    /**
     * Decodes [sourceUri] to a software ARGB_8888 bitmap, or null if unreadable.
     * The long edge is capped so a large photo + the GPEN weights stay within a
     * safe memory budget; faces are still cropped and restored at the model's
     * native 256/512 resolution regardless of the source size.
     */
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
     * Decodes [sourceUri] and runs on-device face detection, returning the
     * decoded image size plus each face's box NORMALIZED to that size (0..1) so
     * the UI can overlay the boxes on a scaled preview. Returns
     * [FaceDetectionResult.EMPTY] when the image can't be read. This mirrors the
     * detection that [restore] performs, so the previewed boxes match what will
     * actually be restored.
     */
    fun detect(sourceUri: String): FaceDetectionResult {
        val source = decodeSource(sourceUri) ?: return FaceDetectionResult.EMPTY
        return try {
            val w = source.width
            val h = source.height
            if (w <= 0 || h <= 0) return FaceDetectionResult.EMPTY
            val boxes = detectFaces(source).map { r ->
                FaceBox(
                    left = (r.left.toFloat() / w).coerceIn(0f, 1f),
                    top = (r.top.toFloat() / h).coerceIn(0f, 1f),
                    right = (r.right.toFloat() / w).coerceIn(0f, 1f),
                    bottom = (r.bottom.toFloat() / h).coerceIn(0f, 1f),
                )
            }
            FaceDetectionResult(imageWidth = w, imageHeight = h, faces = boxes)
        } finally {
            source.recycle()
        }
    }

    /** Runs on-device face detection and returns each face's bounding box. */
    fun detectFaces(source: Bitmap): List<Rect> = try {
        val image = InputImage.fromBitmap(source, 0)
        val faces = Tasks.await(detector.process(image))
        faces.map { it.boundingBox }
    } catch (t: Throwable) {
        Log.w(TAG, "Face detection failed", t)
        emptyList()
    }

    /**
     * Restores every face in [source] with [spec]'s model (loaded from
     * [modelPath]) at the given [strength] and returns the absolute path of the
     * generated PNG. [onProgress] reports completed/total faces. Throws
     * [NoFacesException] when no face is detected, and rethrows on inference
     * failure.
     */
    suspend fun restore(
        source: Bitmap,
        spec: AiModelSpec,
        modelPath: String,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): String {
        val faces = detectFaces(source)
        if (faces.isEmpty()) throw NoFacesException()

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            // GPEN is StyleGAN-based with heavy op fallback: keep it on the CPU provider.
            inferenceEngine.acquireSession(modelPath, allowXnnpack = false).use { session ->
                onProgress(0, faces.size)
                faces.forEachIndexed { index, box ->
                    currentCoroutineContext().ensureActive()
                    restoreFace(session, source, output, box, spec, strength)
                    onProgress(index + 1, faces.size)
                }
            }
            return writePng(output).absolutePath
        } finally {
            output.recycle()
        }
    }

    /**
     * Restores a single face at [box]: expand to a square crop, run the model,
     * de-normalize the output, and feather-blend the restored face back into
     * [output] at the source location.
     */
    private fun restoreFace(
        session: InferenceSession,
        source: Bitmap,
        output: Bitmap,
        box: Rect,
        spec: AiModelSpec,
        strength: Float,
    ) {
        val square = squareCrop(box, source.width, source.height) ?: return
        val crop = Bitmap.createBitmap(source, square.left, square.top, square.width(), square.height())
        val restored = try {
            val input = TensorImageUtils.toInputTensor(crop, spec.io)
            val out = session.run(spec.io.inputName, input, spec.io.outputName)
            imageFromSignedTensor(out)
        } finally {
            // keep crop for blending below; recycled after use
        }
        val restoredScaled = if (restored.width == square.width() && restored.height == square.height()) {
            restored
        } else {
            restored.scale(square.width(), square.height())
        }
        try {
            blendInto(output, restoredScaled, square, strength)
        } finally {
            crop.recycle()
            if (restoredScaled != restored) restoredScaled.recycle()
            restored.recycle()
        }
    }

    /**
     * Expands the detected [box] into a square crop centered on the face with a
     * [CROP_MARGIN] context margin, clamped to stay inside the [width]x[height]
     * image. Returns null if the face is degenerate (zero-sized).
     */
    private fun squareCrop(box: Rect, width: Int, height: Int): Rect? {
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val base = max(box.width(), box.height()) * CROP_MARGIN / 2f
        // Shrink the half-size so the square never leaves the image bounds.
        val half = minOf(base, cx, width - cx, cy, height - cy)
        if (half < 8f) return null
        val left = (cx - half).roundToInt().coerceIn(0, width - 1)
        val top = (cy - half).roundToInt().coerceIn(0, height - 1)
        val side = (half * 2f).roundToInt()
            .coerceAtMost(min(width - left, height - top))
        if (side < 16) return null
        return Rect(left, top, left + side, top + side)
    }

    /**
     * Alpha-blends [restored] (already scaled to the crop size) into [output] at
     * [square]. The per-pixel alpha is [strength] scaled by a soft edge feather
     * so the crop boundary is invisible. The original (opaque) alpha is kept.
     */
    private fun blendInto(output: Bitmap, restored: Bitmap, square: Rect, strength: Float) {
        val w = square.width()
        val h = square.height()
        val orig = IntArray(w * h)
        val rest = IntArray(w * h)
        output.getPixels(orig, 0, w, square.left, square.top, w, h)
        restored.getPixels(rest, 0, w, 0, 0, w, h)
        val featherPx = (min(w, h) * FEATHER_FRACTION).coerceAtLeast(1f)
        for (y in 0 until h) {
            val edgeY = min(y, h - 1 - y).toFloat()
            for (x in 0 until w) {
                val i = y * w + x
                val edge = min(min(x, w - 1 - x).toFloat(), edgeY)
                val feather = (edge / featherPx).coerceIn(0f, 1f)
                val a = (strength * feather).coerceIn(0f, 1f)
                if (a <= 0f) continue
                val o = orig[i]
                val r = rest[i]
                val inv = 1f - a
                val nr = ((r ushr 16 and 0xFF) * a + (o ushr 16 and 0xFF) * inv).roundToInt()
                val ng = ((r ushr 8 and 0xFF) * a + (o ushr 8 and 0xFF) * inv).roundToInt()
                val nb = ((r and 0xFF) * a + (o and 0xFF) * inv).roundToInt()
                orig[i] = (o and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        output.setPixels(orig, 0, w, square.left, square.top, w, h)
    }

    /**
     * Reads an RGB face [tensor] whose channel values are in the GPEN [-1, 1]
     * range (NCHW `[1,3,H,W]` or NHWC `[1,H,W,3]`) into an ARGB_8888 bitmap,
     * de-normalizing with `(v + 1) / 2` and clamping to 0..255.
     */
    private fun imageFromSignedTensor(tensor: FloatTensor): Bitmap {
        val shape = tensor.shape.map { it.toInt() }
        val nchw = shape.size == 4 && shape[1] == 3
        val height: Int
        val width: Int
        if (nchw) {
            height = shape[2]
            width = shape[3]
        } else {
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
            val ri = ((r.coerceIn(-1f, 1f) * 0.5f + 0.5f) * 255f).roundToInt()
            val gi = ((g.coerceIn(-1f, 1f) * 0.5f + 0.5f) * 255f).roundToInt()
            val bi = ((b.coerceIn(-1f, 1f) * 0.5f + 0.5f) * 255f).roundToInt()
            pixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun writePng(bitmap: Bitmap): File {
        val file = modelPaths.newFaceRestoreFile()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private companion object {
        const val TAG = "FaceRestore"

        /** Source long-edge cap; faces are always processed at the model's size. */
        const val MAX_SOURCE_EDGE = 4096

        /**
         * Ignore faces smaller than this fraction of the image's shorter edge.
         * Kept fairly high so tiny background/incidental faces aren't picked up
         * (restoring a face only a few dozen pixels wide adds nothing and just
         * inflates the work); only faces that are a meaningful part of the photo
         * are restored.
         */
        const val MIN_FACE_SIZE = 0.15f

        /** Square crop side = max(face w,h) * this, centered on the face. */
        const val CROP_MARGIN = 1.5f

        /** Edge feather width as a fraction of the crop side (hides the seam). */
        const val FEATHER_FRACTION = 0.18f
    }
}

/** Thrown by [FaceRestoreProcessor.restore] when the photo contains no faces. */
class NoFacesException : Exception("No faces detected in the source image")
