package id.andreasmbngaol.agallery.data.repository

import android.content.IntentSender
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.paging.MediaPagingSource
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class MediaRepositoryImpl(
    private val mediaStore: MediaStoreDataSource,
    private val mediaDao: MediaDao,
) : MediaRepository {

    override fun getMediaPaging(sortOrder: GallerySortOrder): Flow<PagingData<MediaItem>> =
        Pager(
            config = PagingConfig(pageSize = 60, enablePlaceholders = false),
            pagingSourceFactory = { MediaPagingSource(mediaStore, sortOrder) },
        ).flow

    override suspend fun getAlbums(): List<Album> {
        // TODO: map dari mediaStore.queryAlbums()
        return emptyList()
    }

    override suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) {
        if (isFavorite) {
            mediaDao.addFavorite(FavoriteEntity(mediaId, System.currentTimeMillis()))
        } else {
            mediaDao.removeFavorite(mediaId)
        }
    }

    override suspend fun createDeleteRequest(uris: List<String>): IntentSender? =
        mediaStore.buildDeleteRequest(uris.map { Uri.parse(it) })

    override suspend fun getMediaDetails(uri: String): MediaDetails? =
        mediaStore.queryDetails(uri)
}
