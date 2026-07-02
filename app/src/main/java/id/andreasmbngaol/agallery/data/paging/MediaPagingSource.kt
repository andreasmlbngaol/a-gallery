package id.andreasmbngaol.agallery.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem

class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val sortOrder: GallerySortOrder,
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize
        return try {
            val items = dataSource.queryMedia(
                limit = pageSize,
                offset = page * pageSize,
                sortOrder = sortOrder,
            )
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
        }
}
