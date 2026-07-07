package id.andreasmbngaol.agallery.data.mapper

import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaType

/**
 * Map 1 baris cursor MediaStore.Files -> MediaItem (domain).
 * Cursor wajib punya kolom sesuai projection di MediaStoreDataSource.
 */
fun Cursor.toMediaItem(): MediaItem {
    val id = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
    val mediaTypeInt = getInt(getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))

    val type =
        if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) MediaType.VIDEO
        else MediaType.IMAGE

    // URI content:// spesifik per tipe supaya aman dimuat Coil / dibuka viewer.
    val baseUri = when (type) {
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val bucketIdIdx = getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
    val bucketNameIdx = getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

    return MediaItem(
        id = id,
        uri = ContentUris.withAppendedId(baseUri, id).toString(),
        displayName = getStringOrEmpty(MediaStore.Files.FileColumns.DISPLAY_NAME),
        mimeType = getStringOrEmpty(MediaStore.Files.FileColumns.MIME_TYPE),
        type = type,
        dateAddedEpochSeconds = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)),
        durationMs = run {
            val durationIdx = getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            if (type == MediaType.VIDEO && durationIdx >= 0 && !isNull(durationIdx)) getLong(durationIdx) else 0L
        },
        bucketId = if (isNull(bucketIdIdx)) 0L else getLong(bucketIdIdx),
        bucketName = if (isNull(bucketNameIdx)) "" else getString(bucketNameIdx) ?: "",
        isFavorite = false,
    )
}

private fun Cursor.getStringOrEmpty(column: String): String {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) "" else getString(idx) ?: ""
}
