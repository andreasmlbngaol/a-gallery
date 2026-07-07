package id.andreasmbngaol.agallery.data.local.mediastore

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataRemovalOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Removes selected metadata from a photo (1.4.0 feature). Kept separate from
 * [MediaStoreDataSource] because it is a standalone EXIF write operation. The
 * EXIF tag constants live in [MediaExifTags].
 */
class MetadataRemover(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /**
     * Removes the selected metadata from a photo.
     *
     * Only formats that ExifInterface can strip LOSSLESSLY (JPEG/PNG/WebP) are
     * supported; HEIC/HEIF & video -> [MetadataRemovalOutcome.UnsupportedFormat].
     *
     * - [saveAsCopy]=false -> overwrite the original file (needs consent when the
     *   file is not owned by the app and there is no All-files access yet).
     * - [saveAsCopy]=true  -> create a copy in the same folder then strip that
     *   copy (the app owns the copy -> no consent needed). The original stays intact.
     */
    suspend fun removeMetadata(
        uriString: String,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ): MetadataRemovalOutcome = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext MetadataRemovalOutcome.Failed
        val uri = uriString.toUri()

        val projection = arrayOf(
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val info = resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else Triple(
                c.getStringOrNull(0).orEmpty(),
                c.getStringOrNull(1).orEmpty(),
                c.getStringOrNull(2).orEmpty(),
            )
        } ?: return@withContext MetadataRemovalOutcome.Failed

        val (mime, displayName, relativePath) = info
        if (mime.lowercase(Locale.US) !in MediaExifTags.STRIP_SUPPORTED_MIME) {
            return@withContext MetadataRemovalOutcome.UnsupportedFormat
        }

        val tags = tagsToRemove(categories)

        if (saveAsCopy) {
            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanCopyName(displayName))
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (relativePath.isNotEmpty()) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val destUri = resolver.insert(collection, values)
                ?: return@withContext MetadataRemovalOutcome.Failed
            return@withContext try {
                resolver.openInputStream(uri)?.use { input ->
                    resolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
                        ?: error("open output failed")
                } ?: error("open input failed")
                stripTags(destUri, tags)
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(destUri, done, null, null)
                MetadataRemovalOutcome.Success(savedAsCopy = true)
            } catch (_: Throwable) {
                runCatching { resolver.delete(destUri, null, null) }
                MetadataRemovalOutcome.Failed
            }
        }

        return@withContext try {
            stripTags(uri, tags)
            MetadataRemovalOutcome.Success(savedAsCopy = false)
        } catch (e: SecurityException) {
            val sender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
                e is RecoverableSecurityException ->
                    e.userAction.actionIntent.intentSender
                else -> null
            }
            if (sender != null) MetadataRemovalOutcome.NeedsConsent(sender)
            else MetadataRemovalOutcome.Failed
        } catch (_: Throwable) {
            MetadataRemovalOutcome.Failed
        }
    }

    /** Opens a read-write fd, nulls out [tags] & saves (lossless, no re-encode). */
    private fun stripTags(uri: Uri, tags: List<String>) {
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            tags.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
        } ?: error("open fd rw failed")
    }

    /** Inserts "_clean" before the extension so the copy does not overwrite the original. */
    private fun cleanCopyName(original: String): String {
        val dot = original.lastIndexOf('.')
        return if (dot > 0) {
            original.substring(0, dot) + "_clean" + original.substring(dot)
        } else {
            original + "_clean"
        }
    }

    /** Builds the list of EXIF tags to null out based on the selected categories. */
    private fun tagsToRemove(categories: Set<MetadataCategory>): List<String> {
        val out = LinkedHashSet<String>()
        if (MetadataCategory.ALL in categories) {
            out += MediaExifTags.LOCATION_EXIF_TAGS
            out += MediaExifTags.CAMERA_EXIF_TAGS
            out += MediaExifTags.MISC_EXIF_TAGS
        } else {
            if (MetadataCategory.LOCATION in categories) out += MediaExifTags.LOCATION_EXIF_TAGS
            if (MetadataCategory.CAMERA in categories) out += MediaExifTags.CAMERA_EXIF_TAGS
        }
        return out.toList()
    }
}
