package id.andreasmbngaol.agallery.data.local.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.app.RecoverableSecurityException
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Baca media (foto & video) dari MediaStore lewat ContentResolver.
 *
 * ## Kenapa tabel Files (bukan Images/Video terpisah)?
 *
 * Kita query `MediaStore.Files.getContentUri(...)` dengan filter
 * `MEDIA_TYPE IN (IMAGE, VIDEO)`. Alasan:
 *
 * - Foto & video muncul di satu list terurut (kayak Google Photos / iOS Photos).
 * - Cukup satu round-trip cursor, gak perlu merge dua flow.
 *
 * URI per-item tetap dibangun dari koleksi per-tipe (`Images.Media` /
 * `Video.Media`) supaya Coil bisa milih decoder yang tepat.
 *
 * ## Sort + pagination
 *
 * Kita pakai `ContentResolver.query(uri, projection, queryArgs, signal)`
 * dengan `Bundle` (API 26+; project ini minSdk=29). Ini cara resmi buat
 * pass `QUERY_ARG_LIMIT` + `QUERY_ARG_OFFSET` — lebih bersih dan aman
 * daripada nyempilin `LIMIT x OFFSET y` ke string sortOrder (yang di API
 * baru bisa diblok).
 *
 * Tie-break pakai `_ID` biar urutan stabil kalau ada dua foto punya
 * `DATE_ADDED` sama (misal dari import massal).
 */
class MediaStoreDataSource(
    private val context: Context,
) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    private val collectionUri: Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
    )

    suspend fun queryMedia(
        limit: Int,
        offset: Int,
        sortOrder: GallerySortOrder,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val direction = when (sortOrder) {
            GallerySortOrder.DateDesc -> "DESC"
            GallerySortOrder.DateAsc -> "ASC"
        }
        val sortSql =
            "${MediaStore.Files.FileColumns.DATE_ADDED} $direction, " +
                "${MediaStore.Files.FileColumns._ID} $direction"

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortSql)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        contentResolver.query(collectionUri, projection, queryArgs, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durationCol = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            val bucketIdCol = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            buildList(c.count) {
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val mediaType = c.getInt(typeCol)
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        MediaType.VIDEO
                    } else {
                        MediaType.IMAGE
                    }
                    val itemUri: Uri = ContentUris.withAppendedId(
                        when (type) {
                            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        },
                        id,
                    )
                    add(
                        MediaItem(
                            id = id,
                            uri = itemUri.toString(),
                            displayName = c.getString(nameCol).orEmpty(),
                            mimeType = c.getString(mimeCol).orEmpty(),
                            type = type,
                            dateAddedEpochSeconds = c.getLong(dateCol),
                            durationMs = if (type == MediaType.VIDEO && durationCol >= 0 && !c.isNull(durationCol)) {
                                c.getLong(durationCol)
                            } else {
                                0L
                            },
                            bucketId = if (bucketIdCol >= 0) c.getLong(bucketIdCol) else 0L,
                            bucketName = if (bucketNameCol >= 0) {
                                c.getString(bucketNameCol).orEmpty()
                            } else {
                                ""
                            },
                            // TODO: merge dengan tabel Favorites di Room
                            //  (butuh join di layer repository).
                            isFavorite = false,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    /**
     * Bangun permintaan hapus untuk [uris].
     *
     * - API 30+ (R): pakai [MediaStore.createDeleteRequest]. Sistem yang
     *   menampilkan dialog konfirmasi bawaan + benar-benar menghapus filenya.
     *   Kembalikan [IntentSender] yang WAJIB di-launch caller lewat
     *   ActivityResult (StartIntentSenderForResult).
     * - API 29 (Q): coba hapus langsung. Kalau file milik app lain, sistem
     *   melempar [RecoverableSecurityException]; kita kembalikan IntentSender
     *   dari sana supaya user bisa memberi izin.
     *
     * Return null artinya file sudah terhapus langsung (tak perlu launch).
     */
    suspend fun buildDeleteRequest(uris: List<Uri>): IntentSender? =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(contentResolver, uris).intentSender
            } else {
                try {
                    uris.forEach { uri -> contentResolver.delete(uri, null, null) }
                    null
                } catch (e: RecoverableSecurityException) {
                    e.userAction.actionIntent.intentSender
                }
            }
        }

    /**
     * Ambil metadata detail (ukuran, dimensi, folder) untuk satu [uriString].
     * Kolom ini di-query terpisah dari [queryMedia] biar list grid tetap ringan
     * (cukup dipanggil saat user buka panel detail). Return null kalau item tak
     * ada / cursor kosong.
     */
    suspend fun queryDetails(uriString: String): MediaDetails? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val detailProjection = arrayOf(
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
            )
            contentResolver.query(uri, detailProjection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val widthCol = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val pathCol = c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                MediaDetails(
                    sizeBytes = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L,
                    width = if (widthCol >= 0 && !c.isNull(widthCol)) c.getInt(widthCol) else 0,
                    height = if (heightCol >= 0 && !c.isNull(heightCol)) c.getInt(heightCol) else 0,
                    relativePath = if (pathCol >= 0 && !c.isNull(pathCol)) {
                        c.getString(pathCol).orEmpty()
                    } else {
                        ""
                    },
                )
            }
        }

    suspend fun queryAlbums(): List<MediaItem> {
        // TODO: SELECT dengan GROUP BY BUCKET_ID / BUCKET_DISPLAY_NAME +
        //  agregasi COUNT(*) & first-item URI utk cover album.
        return emptyList()
    }
}
