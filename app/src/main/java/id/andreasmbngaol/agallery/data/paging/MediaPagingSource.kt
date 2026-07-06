package id.andreasmbngaol.agallery.data.paging

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.paging.PagingSource
import androidx.paging.PagingState
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope

/**
 * PagingSource offset-based di atas [MediaStoreDataSource].
 *
 * ## Kenapa placeholders + itemsBefore/itemsAfter?
 *
 * Grid dan viewer memakai stream paging TERPISAH (VM berbeda). Supaya posisi
 * tap di grid (`index` absolut) bisa dibuka langsung di viewer, tiap halaman
 * WAJIB tahu posisinya di dalam total (`itemsBefore`) + sisa di bawahnya
 * (`itemsAfter`). Dengan placeholders aktif, `LazyPagingItems.itemCount` =
 * total sebenarnya, jadi index mana pun valid seketika.
 *
 * ## Konsistensi offset
 *
 * `key` = nomor halaman; `offset = page * PAGE_SIZE`. Ini hanya benar kalau
 * SEMUA load berukuran sama, makanya di [androidx.paging.PagingConfig] `initialLoadSize`
 * disetel = `pageSize` (= [PAGE_SIZE]).
 */
class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val sortOrder: GallerySortOrder,
    private val excludeIds: Set<Long> = emptySet(),
    private val scope: MediaScope = MediaScope.Camera,
    // Kalau non-null, HANYA ID yang ada di set ini yang dimuat (dipakai
    // scope Favorites di repository). includeIds kosong -> hasil kosong.
    private val includeIds: Set<Long>? = null,
) : PagingSource<Int, MediaItem>() {

    // Auto re-indexing: begitu MediaStore berubah (foto/video baru, terhapus),
    // batalkan source ini supaya Paging membuat source baru & memuat ulang.
    // getRefreshKey menjaga posisi scroll mendekati anchor semula.
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
