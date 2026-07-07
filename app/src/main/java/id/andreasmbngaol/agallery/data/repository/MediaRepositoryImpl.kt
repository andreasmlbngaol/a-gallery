package id.andreasmbngaol.agallery.data.repository

import android.content.IntentSender
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.data.local.mediastore.ImageFormatConverter
import id.andreasmbngaol.agallery.data.local.mediastore.MediaDetailsReader
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreEditor
import id.andreasmbngaol.agallery.data.local.mediastore.MetadataRemover
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.AlbumCoverEntity
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity
import id.andreasmbngaol.agallery.data.paging.MediaPagingSource
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
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val mediaStore: MediaStoreDataSource,
    private val editor: MediaStoreEditor,
    private val detailsReader: MediaDetailsReader,
    private val metadataRemover: MetadataRemover,
    private val formatConverter: ImageFormatConverter,
    private val mediaDao: MediaDao,
) : MediaRepository {

    // Config paging tunggal -> dipakai bareng galeri utama, album, & favorit.
    private val pagingConfig = PagingConfig(
        pageSize = MediaPagingSource.PAGE_SIZE,
        initialLoadSize = MediaPagingSource.PAGE_SIZE,
        prefetchDistance = MediaPagingSource.PAGE_SIZE * 2,
        enablePlaceholders = true,
    )

    // Sinyal refresh manual. Setiap kali di-increment (via [refreshMedia]),
    // getMediaPaging & observeAlbums membangun ulang sumbernya. Dipakai
    // terutama setelah user memberi izin media (grant tidak selalu memicu
    // ContentObserver MediaStore).
    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMediaPaging(
        sortOrder: GallerySortOrder,
        scope: MediaScope,
    ): Flow<PagingData<MediaItem>> =
        if (scope == MediaScope.Favorites) {
            // Favorit hidup di Room -> gabungkan dgn trash-list (excludeIds)
            // + favorite-list (includeIds). Scope MediaStore = semua media.
            combine(
                mediaDao.observeTrashedIds().map { it.toHashSet() }.distinctUntilChanged(),
                mediaDao.observeFavoriteIds().map { it.toHashSet() }.distinctUntilChanged(),
                refreshTrigger,
            ) { excluded, favs, _ -> excluded to favs }
                .flatMapLatest { (excluded, favs) ->
                    Pager(
                        config = pagingConfig,
                        pagingSourceFactory = {
                            MediaPagingSource(
                                dataSource = mediaStore,
                                sortOrder = sortOrder,
                                excludeIds = excluded,
                                scope = MediaScope.AllMedia,
                                includeIds = favs,
                            )
                        },
                    ).flow
                }
        } else {
            // Scope biasa (kamera/allMedia/videos/screenshots/recordings/bucket).
            // Trash tetap disaring di level SOURCE agar count & offset konsisten.
            combine(
                mediaDao.observeTrashedIds().map { it.toHashSet() }.distinctUntilChanged(),
                refreshTrigger,
            ) { excluded, _ -> excluded }
                .flatMapLatest { excluded ->
                    Pager(
                        config = pagingConfig,
                        pagingSourceFactory = {
                            MediaPagingSource(
                                dataSource = mediaStore,
                                sortOrder = sortOrder,
                                excludeIds = excluded,
                                scope = scope,
                                includeIds = null,
                            )
                        },
                    ).flow
                }
        }

    override suspend fun getAllMedia(
        sortOrder: GallerySortOrder,
        scope: MediaScope,
    ): List<MediaItem> {
        val excluded = mediaDao.observeTrashedIds().first().toHashSet()
        return if (scope == MediaScope.Favorites) {
            val favs = mediaDao.observeFavoriteIds().first().toHashSet()
            mediaStore.queryAllMedia(sortOrder, excluded, MediaScope.AllMedia, favs)
        } else {
            mediaStore.queryAllMedia(sortOrder, excluded, scope, null)
        }
    }

    override suspend fun getAlbums(): List<Album> {
        // Sekali baca daftar favorit, trash, & cover override -> ikut menentukan
        // album cerdas Favorites, penyaringan item trash, & sampul pilihan user.
        val favs = mediaDao.observeFavoriteIds().first().toHashSet()
        val trashed = mediaDao.observeTrashedIds().first().toHashSet()
        val coverOverrides = mediaDao.observeAlbumCovers().first()
            .associate { it.albumKey to it.mediaId }
        return mediaStore.queryAlbums(favs, trashed, coverOverrides)
    }

    override fun observeAlbums(): Flow<List<Album>> =
        combine(
            // contentChanges() sudah emit sekali di awal (snapshot pertama),
            // lalu tiap kali MediaStore berubah.
            mediaStore.contentChanges(),
            mediaDao.observeFavoriteIds().map { it.toHashSet() }.distinctUntilChanged(),
            mediaDao.observeTrashedIds().map { it.toHashSet() }.distinctUntilChanged(),
            mediaDao.observeAlbumCovers().distinctUntilChanged(),
            refreshTrigger,
        ) { _, favs, trashed, covers, _ ->
            val coverOverrides = covers.associate { it.albumKey to it.mediaId }
            mediaStore.queryAlbums(favs, trashed, coverOverrides)
        }

    override suspend fun setAlbumCover(albumKey: String, mediaId: Long) {
        mediaDao.setAlbumCover(AlbumCoverEntity(albumKey = albumKey, mediaId = mediaId))
    }

    override fun refreshMedia() {
        refreshTrigger.value += 1
    }

    override suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) {
        if (isFavorite) {
            mediaDao.addFavorite(FavoriteEntity(mediaId, System.currentTimeMillis()))
        } else {
            mediaDao.removeFavorite(mediaId)
        }
    }

    override fun observeFavoriteIds(): Flow<List<Long>> = mediaDao.observeFavoriteIds()

    override suspend fun moveToTrash(
        mediaId: Long,
        uri: String,
        isVideo: Boolean,
        durationMs: Long,
    ) {
        mediaDao.addTrashed(
            TrashedEntity(
                mediaId = mediaId,
                uri = uri,
                trashedAt = System.currentTimeMillis(),
                isVideo = isVideo,
                durationMs = durationMs,
            ),
        )
    }

    override fun observeTrashItems(): Flow<List<TrashItem>> =
        mediaDao.observeTrashed().map { rows ->
            rows.map { e ->
                TrashItem(
                    id = e.mediaId,
                    uri = e.uri,
                    trashedAt = e.trashedAt,
                    isVideo = e.isVideo,
                    durationMs = e.durationMs,
                )
            }
        }

    override suspend fun restoreFromTrash(mediaId: Long) {
        // Cukup lepas marker; MediaStore row-nya tidak pernah dihapus di
        // soft-delete, jadi item otomatis muncul lagi di grid setelah stream
        // observeTrashedIds emit tanpa id ini.
        mediaDao.removeTrashed(mediaId)
    }

    override suspend fun finalizePermanentDelete(mediaId: Long) {
        // Dipanggil TrashScreen setelah SAF delete-request user-approved.
        // Row Room ikut dihapus supaya tidak jadi ghost record.
        mediaDao.removeTrashed(mediaId)
    }

    override suspend fun purgeExpiredTrash(retentionDays: Int): List<String> {
        // Ambil URI item yg umurnya melewati retensi supaya caller bisa bangun
        // SAF delete-request. Marker Room baru dihapus lewat
        // [finalizePermanentDelete] setelah delete disetujui.
        val threshold =
            System.currentTimeMillis() - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        return mediaDao.getTrashedOlderThan(threshold).map { it.uri }
    }

    override suspend fun autoPurgeExpiredDirectly(retentionDays: Int): Int {
        // Background purge butuh All-files access karena tak bisa tampilkan
        // dialog consent. Tanpa izin -> no-op (item ditahan sampai purge manual).
        if (!editor.hasAllFilesAccess()) return 0
        val threshold =
            System.currentTimeMillis() - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        val expired = mediaDao.getTrashedOlderThan(threshold)
        if (expired.isEmpty()) return 0
        var deleted = 0
        expired.forEach { e ->
            if (editor.deleteDirect(listOf(e.uri.toUri()))) {
                mediaDao.removeTrashed(e.mediaId)
                deleted++
            }
        }
        return deleted
    }

    override suspend fun createDeleteRequest(uris: List<String>): IntentSender? =
        editor.buildDeleteRequest(uris.map { it.toUri() })

    override suspend fun getMediaDetails(uri: String): MediaDetails? =
        detailsReader.queryDetails(uri)

    override suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender? =
        editor.renameMedia(uriString, newDisplayName)

    override suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender? =
        editor.moveMediaToAlbum(uriString, relativePath)

    override suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ) {
        editor.copyMediaToAlbum(uriString, relativePath, displayName, mimeType, isVideo)
    }

    override suspend fun createWriteRequest(uris: List<String>): IntentSender? =
        editor.buildWriteRequest(uris.map { it.toUri() })

    override suspend fun removeMetadata(
        uriString: String,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ): MetadataRemovalOutcome =
        metadataRemover.removeMetadata(uriString, categories, saveAsCopy)

    override suspend fun convertImageFormat(
        uriString: String,
        target: ImageFormat,
        quality: Int,
    ): ConversionOutcome =
        formatConverter.convertImageFormat(uriString, target, quality)
}
