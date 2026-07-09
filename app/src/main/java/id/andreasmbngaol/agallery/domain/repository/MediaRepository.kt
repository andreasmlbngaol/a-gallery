package id.andreasmbngaol.agallery.domain.repository

import android.content.IntentSender
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.conversion.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.conversion.ImageFormat
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.trash.TrashItem
import kotlinx.coroutines.flow.Flow

/**
 * Contract for media access, implemented in the data layer. The presentation and
 * domain layers only know this interface.
 *
 * Note: PagingData comes from androidx.paging (framework). This is a common,
 * pragmatic compromise in Android clean architecture.
 */
interface MediaRepository {
    /**
     * Streams paged media, ordered by [sortOrder] and limited to [scope] (camera,
     * all, videos, screenshots, screen recordings, favorites, or a single
     * folder). Each scope/sort change produces a new Flow; the caller (VM)
     * typically uses `flatMapLatest` so the old PagingSource is cancelled
     * automatically.
     */
    fun getMediaPaging(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<PagingData<MediaItem>>

    /**
     * Returns ALL media for a given [scope] (lightweight metadata) in [sortOrder],
     * exactly matching the grid. Used by the viewer so it can open any index
     * instantly and swipe smoothly without depending on paging. Bitmaps are still
     * loaded lazily per page.
     */
    suspend fun getAllMedia(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): List<MediaItem>

    /**
     * Reactive version of [getAllMedia] for the viewer: re-queries whenever
     * MediaStore changes, the Trash set changes, or [refreshMedia] fires, so
     * trashed/deleted items never linger as blank pages and the viewer stays in
     * sync with the grid.
     */
    fun observeAllMedia(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<List<MediaItem>>

    /** Folder albums plus smart albums (Recent/Camera/Videos/Screenshots/etc.). */
    suspend fun getAlbums(): List<Album>

    /**
     * Reactive version of [getAlbums]. Updates automatically when MediaStore
     * changes, when favorites/Trash change, when a cover override changes, or when
     * [refreshMedia] is triggered. This is the core of album "auto re-indexing"
     * without needing to close and reopen the app.
     */
    fun observeAlbums(): Flow<List<Album>>

    /** Persists the album cover choice ("Set as Cover") -- reactive via [observeAlbums]. */
    suspend fun setAlbumCover(albumKey: String, mediaId: Long)

    /**
     * Forces a refresh of the media sources (grid paging + [observeAlbums]). Used
     * mainly after the user grants media access, since a grant does not always
     * trigger a MediaStore ContentObserver notification.
     */
    fun refreshMedia()

    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    /** Streams the favorited media IDs (Room). Used to render favorite state in the UI. */
    fun observeFavoriteIds(): Flow<List<Long>>

    suspend fun moveToTrash(
        mediaId: Long,
        uri: String,
        isVideo: Boolean,
        durationMs: Long,
    )

    /**
     * Streams the Trash contents (newest first). The source is the Room `trashed`
     * table, not MediaStore. Used by the Trash screen.
     */
    fun observeTrashItems(): Flow<List<TrashItem>>

    /** Restores a single item from Trash -- clears its `trashed` marker. */
    suspend fun restoreFromTrash(mediaId: Long)

    /**
     * Removes the `trashed` marker for [mediaId]. Called BY the Trash screen AFTER
     * the SAF delete request succeeds (the MediaStore file is already gone), so
     * the Room row is removed too and does not become a ghost record.
     */
    suspend fun finalizePermanentDelete(mediaId: Long)

    /**
     * Trash retention auto-purge: returns the URIs of items older than
     * [retentionDays] (e.g. 30) so the caller can build a SAF delete request to
     * remove their files. The Room marker is only removed after the delete is
     * approved (via [finalizePermanentDelete]).
     */
    suspend fun purgeExpiredTrash(retentionDays: Int = 30): List<String>

    /**
     * Purges Trash items older than [retentionDays] DIRECTLY (no dialog). Only
     * runs when the app has All-files access; otherwise returns 0. Used by
     * TrashPurgeWorker for the 30-day background auto-purge. Returns the number of
     * items actually deleted.
     */
    suspend fun autoPurgeExpiredDirectly(retentionDays: Int = 30): Int
    suspend fun getMediaDetails(uri: String): MediaDetails?
    suspend fun createDeleteRequest(uris: List<String>): IntentSender?
    suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender?
    suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender?
    suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    )

    /**
     * Builds a single write request (API 30+) for a BATCH of URIs at once, used by
     * batch move in album detail so the user only approves ONE consent dialog for
     * all items. Null when consent is not needed (All-files access) or the device
     * is below API 30.
     */
    suspend fun createWriteRequest(uris: List<String>): IntentSender?

    /**
     * Strips the selected metadata ([categories]) from a photo. When [saveAsCopy]
     * is true the result is saved as a clean copy (the original stays intact);
     * when false the original file is overwritten. See [MetadataRemovalOutcome]
     * for the result (including NeedsConsent when the file is not owned by the app).
     */
    suspend fun removeMetadata(
        uriString: String,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ): MetadataRemovalOutcome

    /**
     * Converts photo [uriString] to the [target] format ([quality] 1..100 for
     * lossy; PNG ignores it). Always produces a new file in the same folder;
     * deleting the original is decided by the caller. See [ConversionOutcome].
     */
    suspend fun convertImageFormat(
        uriString: String,
        target: ImageFormat,
        quality: Int,
    ): ConversionOutcome
}
