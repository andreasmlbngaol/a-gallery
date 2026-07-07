package id.andreasmbngaol.agallery.data.local.mediastore

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
import id.andreasmbngaol.agallery.domain.model.conversion.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.conversion.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Timeout HeifWriter.stop (ms). */
private const val HEIF_TIMEOUT_MS = 10_000L

/**
 * Image format conversion (1.5.0 feature). Kept separate from
 * [MediaStoreDataSource] because it involves fairly heavy bitmap decode/encode
 * plus HeifWriter. The copied EXIF tag constants live in [MediaExifTags].
 */
class ImageFormatConverter(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /**
     * Converts [uriString] to [target]. ALWAYS creates a new file in the same
     * folder (the original is untouched). [quality] is 1..100 for lossy formats;
     * PNG is lossless. Orientation is baked into the pixels at decode time, then
     * EXIF is copied with orientation reset to normal (JPG/PNG/WEBP; HEIC is
     * limited, via HeifWriter).
     */
    suspend fun convertImageFormat(
        uriString: String,
        target: ImageFormat,
        quality: Int,
    ): ConversionOutcome = withContext(Dispatchers.IO) {
        val srcUri = uriString.toUri()

        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val info = resolver.query(srcUri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else c.getStringOrNull(0).orEmpty() to c.getStringOrNull(1).orEmpty()
        } ?: return@withContext ConversionOutcome.Failed
        val (displayName, relativePath) = info

        val decoded = try {
            val source = ImageDecoder.createSource(resolver, srcUri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (_: Throwable) {
            return@withContext ConversionOutcome.UnsupportedSource
        }

        val bitmap = if (!target.supportsAlpha && decoded.hasAlpha()) {
            flattenOnColor(decoded).also { decoded.recycle() }
        } else {
            decoded
        }

        val outName = uniqueConvertedName(baseName(displayName), target.extension, relativePath)

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
            put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
            if (relativePath.isNotEmpty()) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destUri = resolver.insert(collection, values) ?: run {
            if (!bitmap.isRecycled) bitmap.recycle()
            return@withContext ConversionOutcome.Failed
        }

        return@withContext try {
            val encoded = if (target == ImageFormat.HEIC) {
                encodeHeic(bitmap, destUri, quality)
            } else {
                encodeViaCompress(bitmap, destUri, target, quality)
            }
            if (!encoded) error("encode failed")

            if (target != ImageFormat.HEIC) {
                copyExifResettingOrientation(srcUri, destUri)
            }

            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(destUri, done, null, null)
            ConversionOutcome.Success(outName)
        } catch (_: Throwable) {
            runCatching { resolver.delete(destUri, null, null) }
            if (target == ImageFormat.HEIC) {
                ConversionOutcome.UnsupportedTarget
            } else {
                ConversionOutcome.Failed
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Encode JPG/PNG/WEBP via Bitmap.compress. */
    private fun encodeViaCompress(
        bitmap: Bitmap,
        dest: Uri,
        target: ImageFormat,
        quality: Int,
    ): Boolean {
        val format = when (target) {
            ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            ImageFormat.HEIC -> return false
        }
        return resolver.openOutputStream(dest)?.use { out ->
            bitmap.compress(format, quality, out)
        } ?: false
    }

    /**
     * Encodes HEIC via HeifWriter. HeifWriter needs a file path (the
     * FileDescriptor constructor only exists on API 30+), so write to the cache
     * first then copy into MediaStore. Throws if the HW codec is unavailable ->
     * caught by the caller as UnsupportedTarget.
     */
    @Suppress("RestrictedApi")
    private fun encodeHeic(bitmap: Bitmap, dest: Uri, quality: Int): Boolean {
        val temp = File.createTempFile("convert_", ".heic", context.cacheDir)
        return try {
            val writer = HeifWriter.Builder(
                temp.absolutePath,
                bitmap.width,
                bitmap.height,
                HeifWriter.INPUT_MODE_BITMAP,
            ).setQuality(quality).setMaxImages(1).build()
            writer.start()
            writer.addBitmap(bitmap)
            writer.stop(HEIF_TIMEOUT_MS)
            writer.close()
            resolver.openOutputStream(dest)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
                true
            } ?: false
        } finally {
            runCatching { temp.delete() }
        }
    }

    /** Draws [src] over a solid black canvas (drops alpha). */
    private fun flattenOnColor(src: Bitmap): Bitmap {
        val out = createBitmap(src.width, src.height)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    /**
     * Copies source EXIF tags to the destination, BUT resets orientation to
     * normal (orientation was already baked into the pixels at decode time ->
     * prevents a double rotation). Best-effort: failure does not abort the
     * conversion.
     */
    private fun copyExifResettingOrientation(srcUri: Uri, destUri: Uri) {
        try {
            val srcExif = resolver.openInputStream(srcUri)?.use { ExifInterface(it) } ?: return
            resolver.openFileDescriptor(destUri, "rw")?.use { pfd ->
                val dstExif = ExifInterface(pfd.fileDescriptor)
                MediaExifTags.COPYABLE_EXIF_TAGS.forEach { tag ->
                    srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                }
                dstExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                dstExif.saveAttributes()
            }
        } catch (_: Throwable) {
        }
    }

    private fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** Unique output filename in [relativePath] so it does not overwrite an existing one. */
    private fun uniqueConvertedName(base: String, ext: String, relativePath: String): String {
        var candidate = "$base.$ext"
        var i = 1
        while (nameExists(candidate, relativePath)) {
            candidate = "${base}_$i.$ext"
            i++
        }
        return candidate
    }

    private fun nameExists(name: String, relativePath: String): Boolean {
        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val (sel, args) = if (relativePath.isNotEmpty()) {
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?" to arrayOf(name, relativePath)
        } else {
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?" to arrayOf(name)
        }
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            sel,
            args,
            null,
        )?.use { it.count > 0 } ?: false
    }
}
