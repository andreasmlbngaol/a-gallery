package id.andreasmbngaol.agallery.data.local.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.app.RecoverableSecurityException
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import id.andreasmbngaol.agallery.domain.model.Album
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

    // Filter path folder kamera utk galeri utama. Pola LIKE '%' cocok dgn
    // "DCIM/Camera/" (trailing slash) maupun subfolder di bawahnya.
    private val cameraPathFilter = "DCIM/Camera/%"

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

    /**
     * Tambahkan klausa `_ID NOT IN (...)` supaya media yg di-Trash (atau
     * disembunyikan) tersaring di level QUERY. Ini menjaga count & offset
     * placeholder tetap konsisten dgn viewer — beda dgn memfilter PagingData
     * yg akan bikin placeholder & index viewer desinkron.
     */
    private fun applyExclusion(
        baseSelection: String,
        baseArgs: Array<String>,
        excludeIds: Set<Long>,
    ): Pair<String, Array<String>> {
        if (excludeIds.isEmpty()) return baseSelection to baseArgs
        val placeholders = excludeIds.joinToString(", ") { "?" }
        val selection =
            "$baseSelection AND ${MediaStore.Files.FileColumns._ID} NOT IN ($placeholders)"
        val args = baseArgs + excludeIds.map { it.toString() }
        return selection to args
    }

    suspend fun queryMedia(
        limit: Int,
        offset: Int,
        sortOrder: GallerySortOrder,
        excludeIds: Set<Long> = emptySet(),
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        // Batasi galeri utama HANYA ke folder kamera (DCIM/Camera), bukan
        // semua media di device (Screenshots, Download, WhatsApp, dst).
        val baseSelection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val baseArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            cameraPathFilter,
        )
        val (selection, selectionArgs) = applyExclusion(baseSelection, baseArgs, excludeIds)
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
     * Ambil SELURUH media kamera (tanpa paging) dalam urutan [sortOrder].
     * Delegasi ke [queryMedia] dengan limit maksimum — dipakai viewer supaya
     * bisa buka index mana pun secara instan tanpa jumping paging yang flaky.
     * MediaItem ringan (metadata saja); bitmap tetap di-load lazy oleh Coil.
     */
    suspend fun queryAllMedia(
        sortOrder: GallerySortOrder,
        excludeIds: Set<Long> = emptySet(),
    ): List<MediaItem> =
        queryMedia(
            limit = Int.MAX_VALUE,
            offset = 0,
            sortOrder = sortOrder,
            excludeIds = excludeIds,
        )

    /**
     * Hitung total media kamera (DCIM/Camera). Dipakai [MediaPagingSource]
     * untuk mengisi `itemsBefore`/`itemsAfter` (placeholder) sehingga tiap
     * index bersifat absolut & bisa dibuka langsung di viewer.
     */
    suspend fun countMedia(excludeIds: Set<Long> = emptySet()): Int = withContext(Dispatchers.IO) {
        val baseSelection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val baseArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            cameraPathFilter,
        )
        val (selection, selectionArgs) = applyExclusion(baseSelection, baseArgs, excludeIds)
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
        }
        contentResolver.query(
            collectionUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            queryArgs,
            null,
        )?.use { it.count } ?: 0
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
     * Update satu media by [uri] dengan [values], sambil menangani izin
     * scoped-storage:
     * - Berhasil langsung -> return null.
     * - API 29 & file milik app lain -> [RecoverableSecurityException] ->
     *   kembalikan IntentSender-nya.
     * - API 30+ & file non-owned -> pakai [MediaStore.createWriteRequest].
     *
     * Setelah user memberi izin, caller memanggil ulang operasi yang sama
     * (update kini akan sukses & return null).
     */
    private suspend fun updateWithConsent(uri: Uri, values: ContentValues): IntentSender? =
        withContext(Dispatchers.IO) {
            try {
                contentResolver.update(uri, values, null, null)
                null
            } catch (e: RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createWriteRequest(contentResolver, listOf(uri)).intentSender
                } else {
                    throw e
                }
            }
        }

    /** Ganti DISPLAY_NAME sebuah media. */
    suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender? {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, newDisplayName)
        }
        return updateWithConsent(Uri.parse(uriString), values)
    }

    /**
     * Pindahkan media ke folder [relativePath] (mis. "DCIM/Liburan/") dengan
     * meng-update RELATIVE_PATH — efektif "move" tanpa menyalin byte (API 30+).
     */
    suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender? {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
        }
        return updateWithConsent(Uri.parse(uriString), values)
    }

    /**
     * Salin media ke folder [relativePath] sebagai file BARU: buat entri
     * MediaStore (IS_PENDING=1), stream byte dari sumber, lalu IS_PENDING=0.
     * Tak butuh izin karena app yang membuat file baru. Return URI baru / null.
     */
    suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        val source = Uri.parse(uriString)
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, displayName)
            if (mimeType.isNotEmpty()) {
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            }
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }
        val newUri = contentResolver.insert(collection, values) ?: return@withContext null
        contentResolver.openInputStream(source)?.use { input ->
            contentResolver.openOutputStream(newUri)?.use { output ->
                input.copyTo(output)
            }
        }
        val done = ContentValues().apply {
            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        }
        contentResolver.update(newUri, done, null, null)
        newUri
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

    /**
     * Daftar album (folder) di device: satu entri per BUCKET_ID, berisi nama
     * folder, jumlah item, dan URI item TERBARU sebagai sampul.
     *
     * Berbeda dgn [queryMedia] yg dibatasi ke DCIM/Camera, album menampilkan
     * SEMUA folder (Camera, Screenshots, Download, WhatsApp, dst) supaya user
     * bisa menjelajah semuanya.
     *
     * Diurutkan DATE_ADDED DESC lalu dikelompokkan pakai LinkedHashMap, jadi
     * album dgn item terbaru muncul lebih dulu & item pertama tiap bucket =
     * cover-nya.
     */
    suspend fun queryAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortSql =
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC, " +
                "${MediaStore.Files.FileColumns._ID} DESC"
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortSql)
        }

        contentResolver.query(collectionUri, projection, queryArgs, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdCol = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            // Urutan insert dipertahankan: bucket dgn item terbaru duluan.
            val buckets = LinkedHashMap<Long, AlbumAccumulator>()
            while (c.moveToNext()) {
                if (bucketIdCol < 0 || c.isNull(bucketIdCol)) continue
                val bucketId = c.getLong(bucketIdCol)
                val existing = buckets[bucketId]
                if (existing == null) {
                    val id = c.getLong(idCol)
                    val isVideo =
                        c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val baseUri = if (isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val coverUri = ContentUris.withAppendedId(baseUri, id).toString()
                    val name = if (bucketNameCol >= 0) {
                        c.getString(bucketNameCol).orEmpty()
                    } else {
                        ""
                    }
                    buckets[bucketId] = AlbumAccumulator(name = name, coverUri = coverUri)
                } else {
                    existing.itemCount += 1
                }
            }

            buckets.map { (bucketId, acc) ->
                Album(
                    id = bucketId,
                    name = acc.name.ifEmpty { "Unknown" },
                    coverUri = acc.coverUri,
                    itemCount = acc.itemCount,
                )
            }
        } ?: emptyList()
    }
}

/** Penampung mutable sementara saat mengagregasi album dari cursor. */
private class AlbumAccumulator(
    val name: String,
    val coverUri: String,
    var itemCount: Int = 1,
)
