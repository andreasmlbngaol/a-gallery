package id.andreasmbngaol.agallery.data.local.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import id.andreasmbngaol.agallery.data.mapper.toMediaItem
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_CAMERA
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_FAVORITES
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_RECENT
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_RECORDINGS
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_SCREENSHOTS
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_VIDEOS
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.album.bucketAlbumKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Direct MediaStore data source (photos + videos) for QUERIES & reactivity. All
 * queries run on [Dispatchers.IO].
 *
 * Other operations are SPLIT into separate classes in the same package so this
 * file focuses on a single responsibility (query + observe + album list):
 * - [MediaStoreEditor]      -> delete/rename/move/copy + consent request
 * - [MediaDetailsReader]    -> queryDetails + EXIF/video enrichment
 * - [MetadataRemover]       -> remove selected metadata
 * - [ImageFormatConverter]  -> image format conversion
 *
 * HEIC/HEIF formats load automatically because the `MEDIA_TYPE=IMAGE` filter
 * covers every image regardless of its mime; decoding is handled by Coil (which
 * natively supports HEIF since Android 8+).
 */
class MediaStoreDataSource(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

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

    private val albumProjection: Array<String> = projection + arrayOf(
        MediaStore.Files.FileColumns.RELATIVE_PATH,
    )

    private val cameraPathFilter = "DCIM/Camera/%"

    private val collectionUri: Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val imageType = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    private val videoType = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

    /**
     * Flow that emits whenever the MediaStore collection changes (item added,
     * removed, etc.). Emits once at the start so consumers immediately load the
     * first snapshot. Used for auto re-indexing of the album list.
     */
    fun contentChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collectionUri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** Registers an external observer (used by PagingSource to invalidate). */
    fun registerObserver(observer: ContentObserver) {
        resolver.registerContentObserver(collectionUri, true, observer)
    }

    fun unregisterObserver(observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
    }

    /** Applies an "exclude IDs" filter (e.g. the trash list). */
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

    /** Applies an "only IDs in the set" filter (e.g. the favorites list). */
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
     * Builds the base WHERE clause for [scope]. Always includes the media-type
     * filter (photos & videos) so other files do not leak in.
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
                bothTypes to bothArgs
            }
            MediaScope.Trash -> {
                "1=0" to emptyList()
            }
            MediaScope.AllVideos -> {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to listOf(videoType.toString())
            }
            MediaScope.Screenshots -> {
                val sel = "$bothTypes AND LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE ?"
                sel to (bothArgs + "%screenshots/%")
            }
            MediaScope.ScreenRecordings -> {
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

    /** Same as [queryMedia] but without a limit -- for the viewer. */
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

    /**
     * Folder album accumulator: cover = newest item (because we scan
     * DATE_ADDED DESC), plus photo/video counters.
     */
    private data class AlbumAccumulator(
        var name: String,
        var coverUri: String? = null,
        var photoCount: Int = 0,
        var videoCount: Int = 0,
    )

    /**
     * Smart album accumulator. Because we scan DATE_ADDED DESC, the first
     * matching item automatically becomes the cover.
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
     * Returns the album list (smart albums first, then folders). A single
     * MediaStore scan -> all accumulators are filled together.
     *
     * @param favoriteIds   IDs of favorited media (for the Favorites album).
     * @param trashedIds    IDs of media currently in Trash -> EXCLUDED from
     *   counts & covers, so an album whose content is fully trashed disappears
     *   and a cover never shows an item that is already trashed.
     * @param coverOverrides Map of albumKey -> user-chosen mediaId ("Set as
     *   Cover"). Used only when the item still exists and is not trashed.
     */
    suspend fun queryAlbums(
        favoriteIds: Set<Long>,
        trashedIds: Set<Long> = emptySet(),
        coverOverrides: Map<String, Long> = emptyMap(),
    ): List<Album> = withContext(Dispatchers.IO) {
        val (sel, args) = buildScopeSelection(MediaScope.AllMedia)
        val orderSql = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val folders = LinkedHashMap<Long, AlbumAccumulator>()

        val smartRecent = SmartAccumulator()
        val smartCamera = SmartAccumulator()
        val smartVideos = SmartAccumulator()
        val smartScreenshots = SmartAccumulator()
        val smartRecordings = SmartAccumulator()
        val smartFavorites = SmartAccumulator()

        val cameraBucketIds = HashSet<Long>()
        val screenshotBucketIds = HashSet<Long>()
        val recordingBucketIds = HashSet<Long>()

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
                if (id in trashedIds) continue
                val uri = ContentUris.withAppendedId(collectionUri, id).toString()
                val isVideo = c.getInt(typeCol) == videoType
                val bucketId = c.getLong(bucketIdCol)
                val bucketName = c.getStringOrNull(bucketNameCol).orEmpty()
                val path = c.getStringOrNull(pathCol).orEmpty().lowercase()

                val acc = folders.getOrPut(bucketId) { AlbumAccumulator(name = bucketName) }
                if (acc.name.isEmpty() && bucketName.isNotEmpty()) acc.name = bucketName
                if (acc.coverUri == null) acc.coverUri = uri
                if (isVideo) acc.videoCount++ else acc.photoCount++

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

        fun overrideCover(key: String): String? =
            coverOverrides[key]?.let { overrideUriById[it] }

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
}
