package id.andreasmbngaol.agallery.data.local.mediastore

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import id.andreasmbngaol.agallery.data.mapper.toMediaItem
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_CAMERA
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_FAVORITES
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_RECENT
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_RECORDINGS
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_SCREENSHOTS
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_VIDEOS
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.bucketAlbumKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Sumber data langsung ke MediaStore (foto + video), ditambah I/O tulis via
 * ContentResolver. Semua kueri dijalankan di [Dispatchers.IO].
 *
 * Format HEIC/HEIF ikut ke-load otomatis karena filter `MEDIA_TYPE=IMAGE`
 * mencakup semua image apapun mime-nya; decoding-nya di-handle Coil (yang
 * secara native support HEIF sejak platform Android 8+).
 */
class MediaStoreDataSource(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    // Kolom minimum untuk kueri media (foto + video)
    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
    )

    // Untuk kueri album -> perlu RELATIVE_PATH agar bisa deteksi
    // Screenshots / Screen recordings.
    private val albumProjection: Array<String> = projection + arrayOf(
        MediaStore.Files.FileColumns.RELATIVE_PATH,
    )

    // Konstanta path kamera. Bentuk relatif tanpa slash awal supaya cocok
    // dgn semantik MediaStore.RELATIVE_PATH.
    private val cameraPathFilter = "DCIM/Camera/%"

    // URI koleksi (semua volume eksternal). API 29+.
    private val collectionUri: Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val imageType = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    private val videoType = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

    // -----------------------------------------------------------------
    //  Selection helpers
    // -----------------------------------------------------------------

    /** Terapkan filter "pengecualian ID" (mis. daftar trash). */
    private fun applyExclusion(
        base: String,
        args: List<String>,
        excludeIds: Set<Long>,
    ): Pair<String, Array<String>> {
        if (excludeIds.isEmpty()) return base to args.toTypedArray()
        val placeholders = excludeIds.joinToString(",") { "?" }
        val newSel = "($base) AND ${MediaStore.Files.FileColumns._ID} NOT IN ($placeholders)"
        val newArgs = args + excludeIds.map { it.toString() }
        return newSel to newArgs.toTypedArray()
    }

    /** Terapkan filter "hanya ID di dalam set" (mis. daftar favorit). */
    private fun applyInclusion(
        base: String,
        args: List<String>,
        includeIds: Set<Long>,
    ): Pair<String, Array<String>> {
        val placeholders = includeIds.joinToString(",") { "?" }
        val newSel = "($base) AND ${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
        val newArgs = args + includeIds.map { it.toString() }
        return newSel to newArgs.toTypedArray()
    }

    /**
     * Bangun WHERE clause dasar sesuai [scope]. Selalu mencakup filter
     * jenis media (foto & video) supaya file lain tidak bocor.
     */
    private fun buildScopeSelection(scope: MediaScope): Pair<String, List<String>> {
        val bothTypes = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)"
        val bothArgs = listOf(imageType.toString(), videoType.toString())
        return when (scope) {
            MediaScope.Camera -> {
                val sel = "$bothTypes AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                sel to (bothArgs + cameraPathFilter)
            }
            MediaScope.AllMedia,
            MediaScope.Favorites -> {
                // Favorites di-filter lebih lanjut via applyInclusion(favIds).
                bothTypes to bothArgs
            }
            MediaScope.Trash -> {
                // Trash BUKAN scope MediaStore -- konten diambil langsung dari
                // tabel Room `trashed`. Kalau ada caller yg salah memanggil
                // getMediaPaging(Trash), kembalikan clause "kosong" biar hasil
                // paging pasti empty (bukan crash / bocor semua media).
                "1=0" to emptyList()
            }
            MediaScope.AllVideos -> {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to listOf(videoType.toString())
            }
            MediaScope.Screenshots -> {
                // Cocokkan case-insensitive utk variasi vendor (Pictures/Screenshots,
                // DCIM/Screenshots, dll.).
                val sel = "$bothTypes AND LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE ?"
                sel to (bothArgs + "%screenshots/%")
            }
            MediaScope.ScreenRecordings -> {
                // Screen recordings hanya bertipe video. Vendor menaruh di
                // "Movies/Screen recordings/" (Google/AOSP), "DCIM/Screen recordings/"
                // (beberapa OEM), atau "Movies/ScreenRecorder/" (Xiaomi/OPPO).
                val sel = ("${MediaStore.Files.FileColumns.MEDIA_TYPE}=? " +
                    "AND (LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE ? " +
                    "OR LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE ?)")
                sel to listOf(
                    videoType.toString(),
                    "%screen recordings/%",
                    "%screenrecorder/%",
                )
            }
            is MediaScope.Bucket -> {
                val sel = "$bothTypes AND ${MediaStore.Files.FileColumns.BUCKET_ID}=?"
                sel to (bothArgs + scope.bucketId.toString())
            }
        }
    }

    // -----------------------------------------------------------------
    //  Query media
    // -----------------------------------------------------------------

    suspend fun queryMedia(
        limit: Int,
        offset: Int,
        sortOrder: GallerySortOrder,
        excludeIds: Set<Long> = emptySet(),
        scope: MediaScope = MediaScope.Camera,
        includeIds: Set<Long>? = null,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (includeIds != null && includeIds.isEmpty()) return@withContext emptyList()

        val (baseSel, baseArgs) = buildScopeSelection(scope)
        val (sel1, args1) = applyExclusion(baseSel, baseArgs, excludeIds)
        val (finalSel, finalArgs) = if (includeIds != null) {
            applyInclusion(sel1, args1.toList(), includeIds)
        } else sel1 to args1

        val orderByCol = MediaStore.Files.FileColumns.DATE_ADDED
        val dir = if (sortOrder == GallerySortOrder.DateAsc) "ASC" else "DESC"

        // Android 11+ menolak "LIMIT ... OFFSET ..." di string sortOrder
        // (IllegalArgumentException: Invalid token LIMIT). Pakai Bundle-based
        // query dgn QUERY_ARG_LIMIT/OFFSET. Overload ini tersedia sejak API 26,
        // minSdk kita 29 jadi aman tanpa fallback.
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "$orderByCol $dir")
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            if (finalSel != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, finalSel)
            }
            if (finalArgs != null) {
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, finalArgs)
            }
        }

        val items = mutableListOf<MediaItem>()
        resolver.query(collectionUri, projection, queryArgs, null)?.use { c ->
            while (c.moveToNext()) items += c.toMediaItem()
        }
        items
    }

    suspend fun countMedia(
        excludeIds: Set<Long> = emptySet(),
        scope: MediaScope = MediaScope.Camera,
        includeIds: Set<Long>? = null,
    ): Int = withContext(Dispatchers.IO) {
        if (includeIds != null && includeIds.isEmpty()) return@withContext 0
        val (baseSel, baseArgs) = buildScopeSelection(scope)
        val (sel1, args1) = applyExclusion(baseSel, baseArgs, excludeIds)
        val (finalSel, finalArgs) = if (includeIds != null) {
            applyInclusion(sel1, args1.toList(), includeIds)
        } else sel1 to args1
        resolver.query(
            collectionUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            finalSel,
            finalArgs,
            null,
        )?.use { it.count } ?: 0
    }

    /** Sama dgn [queryMedia] tapi tanpa limit -- utk viewer. */
    suspend fun queryAllMedia(
        sortOrder: GallerySortOrder,
        excludeIds: Set<Long> = emptySet(),
        scope: MediaScope = MediaScope.Camera,
        includeIds: Set<Long>? = null,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (includeIds != null && includeIds.isEmpty()) return@withContext emptyList()

        val (baseSel, baseArgs) = buildScopeSelection(scope)
        val (sel1, args1) = applyExclusion(baseSel, baseArgs, excludeIds)
        val (finalSel, finalArgs) = if (includeIds != null) {
            applyInclusion(sel1, args1.toList(), includeIds)
        } else sel1 to args1

        val orderByCol = MediaStore.Files.FileColumns.DATE_ADDED
        val dir = if (sortOrder == GallerySortOrder.DateAsc) "ASC" else "DESC"
        val orderSql = "$orderByCol $dir"

        val items = mutableListOf<MediaItem>()
        resolver.query(collectionUri, projection, finalSel, finalArgs, orderSql)?.use { c ->
            while (c.moveToNext()) items += c.toMediaItem()
        }
        items
    }

    // -----------------------------------------------------------------
    //  Query albums (folder + album cerdas)
    // -----------------------------------------------------------------

    /**
     * Akumulator album folder: cover = item terbaru (karena kita
     * memindai DATE_ADDED DESC), plus penghitung foto/video.
     */
    private data class AlbumAccumulator(
        var name: String,
        var coverUri: String? = null,
        var photoCount: Int = 0,
        var videoCount: Int = 0,
    )

    /**
     * Akumulator album cerdas. Karena kita memindai DATE_ADDED DESC, item
     * pertama yang match otomatis jadi cover.
     */
    private class SmartAccumulator {
        var coverUri: String? = null
        var photoCount: Int = 0
        var videoCount: Int = 0

        fun add(uri: String, isVideo: Boolean) {
            if (coverUri == null) coverUri = uri
            if (isVideo) videoCount++ else photoCount++
        }

        val itemCount: Int get() = photoCount + videoCount
    }

    /**
     * Kembalikan daftar album (cerdas dulu, lalu folder). Sekali pindai
     * MediaStore -> semua accumulator terisi bareng.
     */
    suspend fun queryAlbums(favoriteIds: Set<Long>): List<Album> = withContext(Dispatchers.IO) {
        val (sel, args) = buildScopeSelection(MediaScope.AllMedia)
        val orderSql = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        // Folder-based accumulators (kunci = bucketId)
        val folders = LinkedHashMap<Long, AlbumAccumulator>()

        // Smart album accumulators
        val smartRecent = SmartAccumulator()
        val smartCamera = SmartAccumulator()
        val smartVideos = SmartAccumulator()
        val smartScreenshots = SmartAccumulator()
        val smartRecordings = SmartAccumulator()
        val smartFavorites = SmartAccumulator()

        resolver.query(
            collectionUri,
            albumProjection,
            sel,
            args.toTypedArray(),
            orderSql,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collectionUri, id).toString()
                val isVideo = c.getInt(typeCol) == videoType
                val bucketId = c.getLong(bucketIdCol)
                val bucketName = c.getStringOrNull(bucketNameCol).orEmpty()
                val path = c.getStringOrNull(pathCol).orEmpty().lowercase()

                // Folder accumulator
                val acc = folders.getOrPut(bucketId) { AlbumAccumulator(name = bucketName) }
                if (acc.name.isEmpty() && bucketName.isNotEmpty()) acc.name = bucketName
                if (acc.coverUri == null) acc.coverUri = uri
                if (isVideo) acc.videoCount++ else acc.photoCount++

                // Smart accumulators
                smartRecent.add(uri, isVideo)
                if (path.startsWith("dcim/camera/")) smartCamera.add(uri, isVideo)
                if (isVideo) smartVideos.add(uri, isVideo)
                if ("screenshots/" in path) smartScreenshots.add(uri, isVideo)
                if (isVideo && ("screen recordings/" in path || "screenrecorder/" in path)) {
                    smartRecordings.add(uri, isVideo)
                }
                if (id in favoriteIds) smartFavorites.add(uri, isVideo)
            }
        }

        // Susun hasil: smart dulu (urutan kanonik), lalu folder.
        val smart = buildList {
            fun addIfAny(key: String, name: String, acc: SmartAccumulator) {
                if (acc.itemCount == 0) return
                add(
                    Album(
                        key = key,
                        scope = smartScopeFromKey(key),
                        name = name,
                        coverUri = acc.coverUri,
                        photoCount = acc.photoCount,
                        videoCount = acc.videoCount,
                        isSmart = true,
                    ),
                )
            }
            addIfAny(ALBUM_KEY_RECENT, "Recent", smartRecent)
            addIfAny(ALBUM_KEY_CAMERA, "Camera", smartCamera)
            addIfAny(ALBUM_KEY_VIDEOS, "Videos", smartVideos)
            addIfAny(ALBUM_KEY_SCREENSHOTS, "Screenshots", smartScreenshots)
            addIfAny(ALBUM_KEY_RECORDINGS, "Screen Recordings", smartRecordings)
            addIfAny(ALBUM_KEY_FAVORITES, "Favorites", smartFavorites)
        }

        val folderAlbums = folders.map { (bucketId, acc) ->
            Album(
                key = bucketAlbumKey(bucketId),
                scope = MediaScope.Bucket(bucketId),
                name = acc.name.ifEmpty { "Unknown" },
                coverUri = acc.coverUri,
                photoCount = acc.photoCount,
                videoCount = acc.videoCount,
                isSmart = false,
            )
        }

        smart + folderAlbums
    }

    private fun smartScopeFromKey(key: String): MediaScope = when (key) {
        ALBUM_KEY_RECENT -> MediaScope.AllMedia
        ALBUM_KEY_CAMERA -> MediaScope.Camera
        ALBUM_KEY_VIDEOS -> MediaScope.AllVideos
        ALBUM_KEY_SCREENSHOTS -> MediaScope.Screenshots
        ALBUM_KEY_RECORDINGS -> MediaScope.ScreenRecordings
        ALBUM_KEY_FAVORITES -> MediaScope.Favorites
        else -> MediaScope.AllMedia
    }

    // -----------------------------------------------------------------
    //  Delete request (SAF / API 30+)
    // -----------------------------------------------------------------

    fun buildDeleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
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
        val uri = Uri.parse(uriString)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
        }
        return updateWithConsent(uri, values)
    }

    fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender? {
        val uri = Uri.parse(uriString)
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
        val srcUri = Uri.parse(uriString)
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

    // -----------------------------------------------------------------
    //  Query details untuk info bottom sheet
    // -----------------------------------------------------------------

    suspend fun queryDetails(uriString: String): MediaDetails? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val proj = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        resolver.query(uri, proj, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@withContext null
            MediaDetails(
                sizeBytes = c.getLong(0),
                width = c.getInt(1),
                height = c.getInt(2),
                relativePath = c.getStringOrNull(3).orEmpty(),
            )
        }
    }

    // -----------------------------------------------------------------
    //  Cache helpers (dipertahankan supaya tak break di modul lain)
    // -----------------------------------------------------------------

    /** Direktori temp share, dipakai VideoPlayerScreen bila perlu. */
    fun cacheDir(): File = context.cacheDir

    fun writeCacheFile(name: String, bytes: ByteArray): File {
        val f = File(context.cacheDir, name)
        FileOutputStream(f).use { it.write(bytes) }
        return f
    }
}
