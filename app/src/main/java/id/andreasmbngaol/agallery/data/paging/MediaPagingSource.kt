package id.andreasmbngaol.agallery.data.paging

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.paging.PagingSource
import androidx.paging.PagingState
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope

/**
 * Offset-based PagingSource on top of [MediaStoreDataSource].
 *
 * ## Why placeholders + itemsBefore/itemsAfter?
 *
 * The grid and the viewer use SEPARATE paging streams (different view models).
 * So that a tap position in the grid (an absolute `index`) can be opened
 * directly in the viewer, every page MUST know its position within the total
 * (`itemsBefore`) and how many items remain below it (`itemsAfter`). With
 * placeholders enabled, `LazyPagingItems.itemCount` equals the real total, so
 * any index is valid immediately.
 *
 * ## Offset consistency
 *
 * `key` is the page number; `offset = page * PAGE_SIZE`. This only holds when
 * EVERY load has the same size, which is why [androidx.paging.PagingConfig]
 * `initialLoadSize` is set to `pageSize` (= [PAGE_SIZE]).
 */
class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val sortOrder: GallerySortOrder,
    private val excludeIds: Set<Long> = emptySet(),
    private val scope: MediaScope = MediaScope.Camera,
    private val includeIds: Set<Long>? = null,
) : PagingSource<Int, MediaItem>() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidate()
        }
    }

    init {
        dataSource.registerObserver(observer)
        registerInvalidatedCallback { dataSource.unregisterObserver(observer) }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val page = params.key ?: 0
        val offset = page * PAGE_SIZE
        return try {
            val total = dataSource.countMedia(excludeIds, scope, includeIds)
            val items = dataSource.queryMedia(
                limit = params.loadSize,
                offset = offset,
                sortOrder = sortOrder,
                excludeIds = excludeIds,
                scope = scope,
                includeIds = includeIds,
            )
            val loadedEnd = offset + items.size
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty() || loadedEnd >= total) null else page + 1,
                itemsBefore = offset.coerceIn(0, total),
                itemsAfter = (total - loadedEnd).coerceAtLeast(0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        return anchor / PAGE_SIZE
    }

    companion object {
        const val PAGE_SIZE = 60
    }
}
