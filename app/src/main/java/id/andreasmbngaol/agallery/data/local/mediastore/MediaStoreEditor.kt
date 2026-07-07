package id.andreasmbngaol.agallery.data.local.mediastore

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.permission.AllFilesAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Operasi TULIS/EDIT ke MediaStore via ContentResolver: hapus, rename, pindah,
 * & salin, termasuk penanganan consent (SAF write/delete request) untuk file
 * yang bukan milik app. Dipisah dari [MediaStoreDataSource] supaya kueri & tulis
 * tidak numpuk di satu file.
 */
class MediaStoreEditor(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    // -----------------------------------------------------------------
    //  Delete request (SAF / API 30+)
    // -----------------------------------------------------------------

    fun buildDeleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        // All-files access ON -> hapus LANGSUNG tanpa dialog konfirmasi sistem.
        if (AllFilesAccess.isGranted()) {
            try {
                uris.forEach { resolver.delete(it, null, null) }
                return null
            } catch (_: SecurityException) {
                // Jatuh ke jalur consent di bawah kalau ternyata tetap ditolak.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(resolver, uris).intentSender
        }
        // Fallback: coba delete langsung. Kalau butuh consent, akan lempar
        // RecoverableSecurityException -> IntentSender bisa diambil.
        return try {
            uris.forEach { resolver.delete(it, null, null) }
            null
        } catch (e: SecurityException) {
            if (e is RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } else null
        }
    }

    /**
     * Bangun write-request untuk BANYAK uri sekaligus (batch move). Null bila
     * All-files access aktif (tak perlu consent) atau perangkat < API 30.
     */
    fun buildWriteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (AllFilesAccess.isGranted()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createWriteRequest(resolver, uris).intentSender
        }
        return null
    }

    /** True bila app punya All-files access (atau OS < 11 yg tak butuh). */
    fun hasAllFilesAccess(): Boolean = AllFilesAccess.isGranted()

    /**
     * Hapus PERMANEN langsung via ContentResolver tanpa dialog. Hanya berhasil
     * bila app punya All-files access (API 30+) atau file milik app sendiri.
     * Dipakai auto-purge Trash di background. Return true kalau semua terhapus.
     */
    fun deleteDirect(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return true
        return try {
            uris.forEach { resolver.delete(it, null, null) }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Setelah user setuju di consent dialog, jalankan block ini. */
    fun updateWithConsent(uri: Uri, values: ContentValues): IntentSender? {
        return try {
            resolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
            } else if (e is RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } else null
        }
    }

    fun renameMedia(uriString: String, newDisplayName: String): IntentSender? {
        val uri = uriString.toUri()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
        }
        return updateWithConsent(uri, values)
    }

    fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender? {
        val uri = uriString.toUri()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        return updateWithConsent(uri, values)
    }

    suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ) = withContext(Dispatchers.IO) {
        val srcUri = uriString.toUri()
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destUri = resolver.insert(collection, values) ?: return@withContext
        try {
            resolver.openInputStream(srcUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(destUri, null, null)
            throw t
        }
    }
}
