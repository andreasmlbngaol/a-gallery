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
 * Bertanggung jawab untuk 3 hal terkait gambar QR: simpan ke galeri (MediaStore),
 * siapkan file cache utk dibagikan (FileProvider), dan decode foto logo jadi
 * [ImageBitmap]. Dipisah dari layer presentation supaya VM tetap bebas Context.
 */
class QrImageSaver(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /** Simpan [bitmap] PNG ke galeri di folder "Pictures/AGallery QR". */
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
     * Tulis PNG ke cache lalu bungkus jadi content:// uri via FileProvider supaya
     * bisa dibagikan lewat share sheet (ACTION_SEND) tanpa menyentuh galeri.
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

    /** Decode foto logo (di-downscale) jadi [ImageBitmap] utk digambar di kanvas. */
    suspend fun loadLogoBitmap(uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(resolver, uri.toUri())
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(2)
                decoder.isMutableRequired = false
            }
            // Software config wajib supaya aman digambar & di-capture ke bitmap.
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
