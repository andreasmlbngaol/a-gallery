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
            // Placeholders ON: itemCount == total sebenarnya, jadi index absolut
            // (posisi tap di grid) valid & bisa langsung dibuka di viewer.
            // initialLoadSize = pageSize: wajib supaya offset (page * PAGE_SIZE)
            // konsisten di semua load, tanpa overlap antar halaman. Viewer tidak
            // lagi pakai jumping paging (diganti daftar penuh via getAllMedia).
            config = PagingConfig(
                pageSize = MediaPagingSource.PAGE_SIZE,
                initialLoadSize = MediaPagingSource.PAGE_SIZE,
                // Muat 2 halaman di depan (bukan default 1 halaman) supaya saat
                // scroll cepat lebih jarang ketemu placeholder kosong.
                prefetchDistance = MediaPagingSource.PAGE_SIZE * 2,
                enablePlaceholders = true,
            ),
            pagingSourceFactory = { MediaPagingSource(mediaStore, sortOrder) },
        ).flow

    override suspend fun getAllMedia(sortOrder: GallerySortOrder): List<MediaItem> =
        mediaStore.queryAllMedia(sortOrder)

    override suspend fun getAlbums(): List<Album> = mediaStore.queryAlbums()

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
