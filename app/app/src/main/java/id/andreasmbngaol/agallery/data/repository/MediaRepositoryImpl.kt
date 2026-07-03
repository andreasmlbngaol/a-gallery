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
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val mediaStore: MediaStoreDataSource,
    private val mediaDao: MediaDao,
) : MediaRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMediaPaging(sortOrder: GallerySortOrder): Flow<PagingData<MediaItem>> =
        // Sembunyikan media yg ada di Trash. Filter di level SOURCE (bukan
        // PagingData.filter) supaya count/offset placeholder tetap konsisten
        // dgn viewer (getAllMedia). Tiap perubahan isi Trash → PagingSource
        // dibuat ulang (flatMapLatest) sehingga item ter-trash langsung hilang.
        mediaDao.observeTrashedIds()
            .map { it.toHashSet() }
            .distinctUntilChanged()
            .flatMapLatest { excluded ->
                Pager(
                    // Placeholders ON: itemCount == total sebenarnya, jadi index
                    // absolut (tap di grid) valid & langsung dibuka di viewer.
                    // initialLoadSize = pageSize: wajib supaya offset
                    // (page * PAGE_SIZE) konsisten di semua load, tanpa overlap.
                    config = PagingConfig(
                        pageSize = MediaPagingSource.PAGE_SIZE,
                        initialLoadSize = MediaPagingSource.PAGE_SIZE,
                        prefetchDistance = MediaPagingSource.PAGE_SIZE * 2,
                        enablePlaceholders = true,
                    ),
                    pagingSourceFactory = {
                        MediaPagingSource(mediaStore, sortOrder, excluded)
                    },
                ).flow
            }

    override suspend fun getAllMedia(sortOrder: GallerySortOrder): List<MediaItem> {
        // Viewer juga harus melewati item yg ada di Trash agar sinkron dgn grid.
        val excluded = mediaDao.observeTrashedIds().first().toHashSet()
        return mediaStore.queryAllMedia(sortOrder, excluded)
    }

    override suspend fun getAlbums(): List<Album> = mediaStore.queryAlbums()

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
