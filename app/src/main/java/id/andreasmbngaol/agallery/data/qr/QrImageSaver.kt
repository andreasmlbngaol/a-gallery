package id.andreasmbngaol.agallery.data.qr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles three QR-image concerns: saving to the gallery (MediaStore), preparing
 * a cache file for sharing (FileProvider), and decoding a logo photo into an
 * [ImageBitmap]. Kept out of the presentation layer so view models stay free of
 * Context.
 */
class QrImageSaver(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /** Saves [bitmap] as a PNG to the gallery under "Pictures/AGallery QR". */
    suspend fun saveToGallery(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val name = "QR_${System.currentTimeMillis()}.png"
        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery QR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(collection, values) ?: return@withContext false
        return@withContext try {
            val ok = resolver.openOutputStream(dest)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
            if (!ok) error("compress failed")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(dest, done, null, null)
            true
        } catch (_: Throwable) {
            runCatching { resolver.delete(dest, null, null) }
            false
        }
    }

    /**
     * Writes a PNG to the cache then wraps it as a content:// uri via FileProvider
     * so it can be shared through the share sheet (ACTION_SEND) without touching
     * the gallery.
     */
    suspend fun cacheForShare(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared_qr").apply { mkdirs() }
            val file = File(dir, "QR_${System.currentTimeMillis()}.png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            null
        }
    }

    /** Decodes the (downscaled) logo photo into an [ImageBitmap] for canvas drawing. */
    suspend fun loadLogoBitmap(uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(resolver, uri.toUri())
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(2)
                decoder.isMutableRequired = false
            }
            val safe = if (decoded.config == Bitmap.Config.HARDWARE) {
                decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            } else {
                decoded
            }
            safe.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
