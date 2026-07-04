package id.andreasmbngaol.agallery.data.repository

import android.content.IntentSender
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity
import id.andreasmbngaol.agallery.data.paging.MediaPagingSource
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val mediaStore: MediaStoreDataSource,
    private val mediaDao: MediaDao,
) : MediaRepository {

    // Config paging tunggal -> dipakai bareng galeri utama, album, & favorit.
    private val pagingConfig = PagingConfig(
        pageSize = MediaPagingSource.PAGE_SIZE,
        initialLoadSize = MediaPagingSource.PAGE_SIZE,
        prefetchDistance = MediaPagingSource.PAGE_SIZE * 2,
        enablePlaceholders = true,
    )

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
            ) { excluded, favs -> excluded to favs }
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
            mediaDao.observeTrashedIds()
                .map { it.toHashSet() }
                .distinctUntilChanged()
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
        // Sekali baca daftar favorit -> ikut jadi salah satu album cerdas.
        val favs = mediaDao.observeFavoriteIds().first().toHashSet()
        return mediaStore.queryAlbums(favs)
    }

    override suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) {
        if (isFavorite) {
            mediaDao.addFavorite(FavoriteEntity(mediaId, System.currentTimeMillis()))
        } else {
            mediaDao.removeFavorite(mediaId)
        }
    }

    override fun observeFavoriteIds(): Flow<List<Long>> = mediaDao.observeFavoriteIds()

    override suspend fun moveToTrash(mediaId: Long, uri: String) {
        mediaDao.addTrashed(
            TrashedEntity(
                mediaId = mediaId,
                uri = uri,
                trashedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeTrashItems(): Flow<List<id.andreasmbngaol.agallery.domain.model.TrashItem>> =
        mediaDao.observeTrashed().map { rows ->
            rows.map { e ->
                id.andreasmbngaol.agallery.domain.model.TrashItem(
                    id = e.mediaId,
                    uri = e.uri,
                    trashedAt = e.trashedAt,
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

    override suspend fun createDeleteRequest(uris: List<String>): IntentSender? =
        mediaStore.buildDeleteRequest(uris.map { Uri.parse(it) })

    override suspend fun getMediaDetails(uri: String): MediaDetails? =
        mediaStore.queryDetails(uri)

    override suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender? =
        mediaStore.renameMedia(uriString, newDisplayName)

    override suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender? =
        mediaStore.moveMediaToAlbum(uriString, relativePath)

    override suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ) {
        mediaStore.copyMediaToAlbum(uriString, relativePath, displayName, mimeType, isVideo)
    }
}
