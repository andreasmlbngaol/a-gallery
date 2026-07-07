package id.andreasmbngaol.agallery.data.local.mediastore

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import androidx.core.graphics.createBitmap
import id.andreasmbngaol.agallery.core.permission.AllFilesAccess
import id.andreasmbngaol.agallery.data.mapper.toMediaItem
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_CAMERA
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_FAVORITES
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_RECENT
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_RECORDINGS
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_SCREENSHOTS
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_VIDEOS
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.ImageFormat
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.MetadataRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.bucketAlbumKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
    //  Reaktivitas: pantau perubahan MediaStore (foto/video baru/terhapus)
    // -----------------------------------------------------------------

    /**
     * Flow yang emit setiap kali koleksi MediaStore berubah (item baru,
     * terhapus, dsb). Emit sekali di awal supaya konsumer langsung memuat
     * snapshot pertama. Dipakai untuk auto re-indexing daftar album.
     */
    fun contentChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collectionUri, true, observer)
        trySend(Unit) // snapshot awal
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** Daftarkan observer eksternal (dipakai PagingSource utk invalidate). */
    fun registerObserver(observer: ContentObserver) {
        resolver.registerContentObserver(collectionUri, true, observer)
    }

    fun unregisterObserver(observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
    }

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
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, finalSel)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, finalArgs)
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
     *
     * @param favoriteIds   ID media yg difavoritkan (utk album Favorites).
     * @param trashedIds    ID media yg sedang di Trash -> DIKECUALIKAN dari
     *   hitungan & cover, sehingga album yg isinya habis ke-trash ikut hilang
     *   dan cover tidak pernah menampilkan item yg sudah di-trash.
     * @param coverOverrides Map albumKey -> mediaId pilihan user ("Set as
     *   Cover"). Dipakai hanya bila item-nya masih ada & tidak di-trash.
     */
    suspend fun queryAlbums(
        favoriteIds: Set<Long>,
        trashedIds: Set<Long> = emptySet(),
        coverOverrides: Map<String, Long> = emptyMap(),
    ): List<Album> = withContext(Dispatchers.IO) {
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

        // Bucket yang identik dgn album cerdas "folder-like" (Camera,
        // Screenshots, Screen Recordings). Dikumpulkan supaya folder-nya TIDAK
        // dobel muncul di section "More" (album cerdas sudah mewakilinya).
        val cameraBucketIds = HashSet<Long>()
        val screenshotBucketIds = HashSet<Long>()
        val recordingBucketIds = HashSet<Long>()

        // URI utk id yg jadi target "Set as Cover" (hanya yg tidak di-trash,
        // karena item trash sudah di-`continue` sebelum tercatat).
        val overrideIds = coverOverrides.values.toHashSet()
        val overrideUriById = HashMap<Long, String>()

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
                // Item yg sedang di Trash tidak boleh ikut dihitung/di-cover.
                if (id in trashedIds) continue
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
                if (path.startsWith("dcim/camera/")) {
                    smartCamera.add(uri, isVideo)
                    cameraBucketIds.add(bucketId)
                }
                if (isVideo) smartVideos.add(uri, true)
                if ("screenshots/" in path) {
                    smartScreenshots.add(uri, isVideo)
                    screenshotBucketIds.add(bucketId)
                }
                if (isVideo && ("screen recordings/" in path || "screenrecorder/" in path)) {
                    smartRecordings.add(uri, true)
                    recordingBucketIds.add(bucketId)
                }
                if (id in favoriteIds) smartFavorites.add(uri, isVideo)

                if (id in overrideIds) overrideUriById[id] = uri
            }
        }

        // Cover pilihan user utk sebuah albumKey (null bila item-nya sudah
        // tidak ada / di-trash -> otomatis fallback ke cover default).
        fun overrideCover(key: String): String? =
            coverOverrides[key]?.let { overrideUriById[it] }

        // Susun hasil: smart dulu (urutan kanonik), lalu folder.
        val smart = buildList {
            fun addIfAny(key: String, name: String, acc: SmartAccumulator) {
                if (acc.itemCount == 0) return
                add(
                    Album(
                        key = key,
                        scope = smartScopeFromKey(key),
                        name = name,
                        coverUri = overrideCover(key) ?: acc.coverUri,
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

        val folderAlbums = folders
            // Buang folder yg sudah diwakili album cerdas folder-like (hindari
            // duplikat visual di "More") + folder yg jadi kosong akibat semua
            // item-nya di-trash.
            .filterKeys {
                it !in cameraBucketIds &&
                    it !in screenshotBucketIds &&
                    it !in recordingBucketIds
            }
            .mapNotNull { (bucketId, acc) ->
                if (acc.photoCount + acc.videoCount == 0) return@mapNotNull null
                val key = bucketAlbumKey(bucketId)
                Album(
                    key = key,
                    scope = MediaScope.Bucket(bucketId),
                    name = acc.name.ifEmpty { "Unknown" },
                    coverUri = overrideCover(key) ?: acc.coverUri,
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

    // -----------------------------------------------------------------
    //  Query details untuk info bottom sheet
    // -----------------------------------------------------------------

    suspend fun queryDetails(uriString: String): MediaDetails? = withContext(Dispatchers.IO) {
        val uri = uriString.toUri()
        val proj = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val base = resolver.query(uri, proj, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            MediaDetails(
                sizeBytes = c.getLong(0),
                width = c.getInt(1),
                height = c.getInt(2),
                relativePath = c.getStringOrNull(3).orEmpty(),
            ) to c.getStringOrNull(4).orEmpty()
        } ?: return@withContext null

        val (details, mime) = base
        // Perkaya dgn metadata teknis. Semua dibungkus try/catch supaya file
        // tanpa metadata (mis. screenshot) tetap aman -> field ekstra null.
        if (mime.startsWith("video")) enrichVideo(uri, details) else enrichImage(uri, details)
    }

    /** Baca EXIF foto: kamera, exposure, flash, tanggal ambil & GPS. */
    private fun enrichImage(uri: Uri, base: MediaDetails): MediaDetails = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)

            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            val isoInt = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            val flashRaw = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            val latLong = exif.latLong
            base.copy(
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifEmpty { null },
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifEmpty { null },
                aperture = if (fNumber > 0.0) "f/" + trimDecimal(fNumber) else null,
                shutterSpeed = formatShutter(exposure),
                iso = if (isoInt > 0) isoInt.toString() else null,
                focalLength = if (focal > 0.0) trimDecimal(focal) + " mm" else null,
                flashFired = if (flashRaw < 0) null else (flashRaw and 0x1) == 1,
                dateTakenEpochSeconds = parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
            )
        } ?: base
    } catch (_: Exception) {
        base
    }

    /** Baca metadata teknis video: bitrate, dimensi, lokasi, codec & fps. */
    private fun enrichVideo(uri: Uri, base: MediaDetails): MediaDetails {
        var result = base

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { formatBitrate(it) }
            val w = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val (lat, lng) = parseIso6709(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            )
            result = result.copy(
                width = if (result.width > 0) result.width else w,
                height = if (result.height > 0) result.height else h,
                bitrate = bitrate,
                latitude = lat,
                longitude = lng,
            )
        } catch (_: Exception) {
            // biarkan result apa adanya
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var videoCodec: String? = null
            var audioCodec: String? = null
            var frameRate: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    trackMime.startsWith("video/") && videoCodec == null -> {
                        videoCodec = friendlyCodec(trackMime)
                        frameRate = extractFrameRate(format)
                    }
                    trackMime.startsWith("audio/") && audioCodec == null -> {
                        audioCodec = friendlyCodec(trackMime)
                    }
                }
            }
            result = result.copy(
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                frameRate = frameRate,
            )
        } catch (_: Exception) {
            // biarkan result apa adanya
        } finally {
            try { extractor.release() } catch (_: Exception) { }
        }

        return result
    }

    private fun extractFrameRate(format: MediaFormat): String? = try {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            null
        } else {
            val fps = try {
                format.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (_: Exception) {
                format.getFloat(MediaFormat.KEY_FRAME_RATE).toInt()
            }
            if (fps > 0) "$fps fps" else null
        }
    } catch (_: Exception) {
        null
    }

    private fun friendlyCodec(mime: String): String = when (mime.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265 (HEVC)"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/raw" -> "PCM"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        else -> mime.substringAfter('/').uppercase()
    }

    private fun formatBitrate(bps: Long): String =
        if (bps >= 1_000_000L) {
            String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        } else {
            String.format(Locale.US, "%.0f Kbps", bps / 1_000.0)
        }

    private fun trimDecimal(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    private fun formatShutter(seconds: Double): String? = when {
        seconds <= 0.0 -> null
        seconds < 1.0 -> "1/${(1.0 / seconds).roundToInt()} s"
        else -> trimDecimal(seconds) + " s"
    }

    private fun parseExifDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime
                .parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toEpochSecond()
        } catch (_: Exception) {
            null
        }
    }

    /** Parse string lokasi ISO-6709 (mis "+37.42-122.08/") -> lat/long. */
    private fun parseIso6709(raw: String?): Pair<Double?, Double?> {
        if (raw.isNullOrBlank()) return null to null
        return try {
            val cleaned = raw.trim().removeSuffix("/")
            val match = Regex("""([+\-]\d+(?:\.\d+)?)([+\-]\d+(?:\.\d+)?)""")
                .find(cleaned) ?: return null to null
            match.groupValues[1].toDoubleOrNull() to match.groupValues[2].toDoubleOrNull()
        } catch (_: Exception) {
            null to null
        }
    }

    // -----------------------------------------------------------------
    //  Hapus metadata (fitur 1.4.0)
    // -----------------------------------------------------------------

    /**
     * Buang metadata terpilih dari sebuah foto.
     *
     * Hanya format yang bisa di-strip LOSSLESS oleh ExifInterface (JPEG/PNG/WebP)
     * yang didukung; HEIC/HEIF & video -> [MetadataRemovalOutcome.UnsupportedFormat].
     *
     * - [saveAsCopy]=false -> timpa file asli (butuh consent kalau file bukan
     *   milik app & belum punya All-files access).
     * - [saveAsCopy]=true  -> buat salinan di folder yg sama lalu strip salinan
     *   itu (app pemilik salinan -> tak perlu consent). File asli tetap utuh.
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
        if (mime.lowercase(Locale.US) !in STRIP_SUPPORTED_MIME) {
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

        // Timpa file asli.
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

    /** Buka fd read-write lalu null-kan [tags] & simpan (lossless, tanpa re-encode). */
    private fun stripTags(uri: Uri, tags: List<String>) {
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            tags.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
        } ?: error("open fd rw failed")
    }

    /** Sisipkan "_clean" sebelum ekstensi supaya salinan tak menimpa asli. */
    private fun cleanCopyName(original: String): String {
        val dot = original.lastIndexOf('.')
        return if (dot > 0) {
            original.substring(0, dot) + "_clean" + original.substring(dot)
        } else {
            original + "_clean"
        }
    }

    /** Susun daftar tag EXIF yang mau di-null berdasarkan kategori terpilih. */
    private fun tagsToRemove(categories: Set<MetadataCategory>): List<String> {
        val out = LinkedHashSet<String>()
        if (MetadataCategory.ALL in categories) {
            out += LOCATION_EXIF_TAGS
            out += CAMERA_EXIF_TAGS
            out += MISC_EXIF_TAGS
        } else {
            if (MetadataCategory.LOCATION in categories) out += LOCATION_EXIF_TAGS
            if (MetadataCategory.CAMERA in categories) out += CAMERA_EXIF_TAGS
        }
        return out.toList()
    }

    // -----------------------------------------------------------------
    //  Format Converter (1.5.0)
    // -----------------------------------------------------------------

    /**
     * Konversi [uriString] ke [target]. SELALU bikin file baru di folder yg
     * sama (asli tak disentuh). [quality] 1..100 utk lossy; PNG lossless.
     * Orientasi otomatis dibakar ke pixel saat decode, lalu EXIF disalin dgn
     * orientasi di-reset normal (JPG/PNG/WEBP; HEIC terbatas via HeifWriter).
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

        // 1) Decode -> software bitmap (biar bisa di-compress; orientasi dibakar).
        val decoded = try {
            val source = ImageDecoder.createSource(resolver, srcUri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (_: Throwable) {
            return@withContext ConversionOutcome.UnsupportedSource
        }

        // 2) Flatten transparansi ke hitam kalau target tanpa alpha (JPG/HEIC).
        val bitmap = if (!target.supportsAlpha && decoded.hasAlpha()) {
            flattenOnColor(decoded).also { decoded.recycle() }
        } else {
            decoded
        }

        // 3) Nama hasil unik di folder yg sama.
        val outName = uniqueConvertedName(baseName(displayName), target.extension, relativePath)

        // 4) Insert entri pending.
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

            // Salin EXIF (kecuali HEIC yg tak didukung tulis EXIF-nya).
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
     * Encode HEIC via HeifWriter. HeifWriter butuh path file (konstruktor
     * FileDescriptor baru ada di API 30), jadi tulis ke cache dulu lalu salin ke
     * MediaStore. Lempar exception kalau codec HW tak tersedia -> ditangkap
     * pemanggil jadi UnsupportedTarget.
     */
    // HeifWriter.close() (warisan WriterBase) keannotasi @RestrictTo di 1.0.0,
    // padahal memang harus dipanggil consumer utk melepas resource. Suppress.
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

    /** Gambar [src] di atas kanvas berwarna hitam (buang alpha). */
    private fun flattenOnColor(src: Bitmap): Bitmap {
        val out = createBitmap(src.width, src.height)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    /**
     * Salin tag EXIF sumber ke tujuan, TAPI reset orientasi ke normal (karena
     * orientasi sudah dibakar ke pixel saat decode -> mencegah double-rotate).
     * Best-effort: kegagalan tak membatalkan konversi.
     */
    private fun copyExifResettingOrientation(srcUri: Uri, destUri: Uri) {
        try {
            val srcExif = resolver.openInputStream(srcUri)?.use { ExifInterface(it) } ?: return
            resolver.openFileDescriptor(destUri, "rw")?.use { pfd ->
                val dstExif = ExifInterface(pfd.fileDescriptor)
                COPYABLE_EXIF_TAGS.forEach { tag ->
                    srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                }
                dstExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                dstExif.saveAttributes()
            }
        } catch (_: Throwable) {
            // EXIF opsional.
        }
    }

    private fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** Nama file hasil unik di [relativePath] biar tak menimpa yg sudah ada. */
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

    companion object {
        /** Format yang bisa di-strip lossless oleh ExifInterface. */
        private val STRIP_SUPPORTED_MIME = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp",
        )

        /** Semua tag GPS/lokasi. */
        private val LOCATION_EXIF_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
        )

        /** Tag perangkat + pengaturan pemotretan (termasuk kapan diambil). */
        private val CAMERA_EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_MAX_APERTURE_VALUE, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_EXPOSURE_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_EXPOSURE_INDEX,
            // Catatan: TAG_ISO_SPEED_RATINGS deprecated & 1:1 dgn tag di bawah
            // (EXIF 0x8827), jadi cukup pakai TAG_PHOTOGRAPHIC_SENSITIVITY.
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FLASH_ENERGY, ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_SCENE_TYPE, ExifInterface.TAG_SENSING_METHOD,
            ExifInterface.TAG_CONTRAST, ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS, ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        )

        /**
         * Info personal lain (hanya dipakai saat "Semua"). Orientasi SENGAJA
         * tak dimasukkan supaya foto tak jadi miring setelah di-strip.
         */
        private val MISC_EXIF_TAGS = listOf(
            ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_SUBSEC_TIME,
        )

        /** Semua tag yg disalin saat konversi (orientasi ditangani terpisah). */
        private val COPYABLE_EXIF_TAGS: List<String> =
            (LOCATION_EXIF_TAGS + CAMERA_EXIF_TAGS + MISC_EXIF_TAGS).distinct()

        /** Timeout HeifWriter.stop (ms). */
        private const val HEIF_TIMEOUT_MS = 10_000L
    }

    // -----------------------------------------------------------------
    //  Cache helpers (dipertahankan supaya tak break di modul lain)
    // -----------------------------------------------------------------

    /** Direktori temp share, dipakai VideoPlayerScreen bila perlu. */
//    fun cacheDir(): File = context.cacheDir
//
//    fun writeCacheFile(name: String, bytes: ByteArray): File {
//        val f = File(context.cacheDir, name)
//        FileOutputStream(f).use { it.write(bytes) }
//        return f
//    }
}
