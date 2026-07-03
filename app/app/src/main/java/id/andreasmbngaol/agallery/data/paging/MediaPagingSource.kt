package id.andreasmbngaol.agallery.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem

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
 * SEMUA load berukuran sama, makanya di [PagingConfig] `initialLoadSize`
 * disetel = `pageSize` (= [PAGE_SIZE]). Kalau beda (mis. default 3x), offset
 * awal akan tumpang-tindih dengan halaman berikutnya.
 */
class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val sortOrder: GallerySortOrder,
    // ID media yg disembunyikan (mis. ada di Trash). Diteruskan ke query agar
    // count & offset tetap akurat.
    private val excludeIds: Set<Long> = emptySet(),
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val page = params.key ?: 0
        val offset = page * PAGE_SIZE
        return try {
            val total = dataSource.countMedia(excludeIds)
            val items = dataSource.queryMedia(
                limit = params.loadSize,
                offset = offset,
                sortOrder = sortOrder,
                excludeIds = excludeIds,
            )
            val loadedEnd = offset + items.size
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty() || loadedEnd >= total) null else page + 1,
                // Posisi absolut halaman ini di dalam total (untuk placeholders).
                itemsBefore = offset.coerceIn(0, total),
                itemsAfter = (total - loadedEnd).coerceAtLeast(0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Saat refresh/invalidate (jump, pull-to-refresh, atau setelah delete),
     * mulai dari halaman yang memuat posisi terakhir dilihat user — bukan dari
     * 0 — supaya posisi scroll & halaman viewer tetap sinkron.
     */
    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        return anchor / PAGE_SIZE
    }

    companion object {
        const val PAGE_SIZE = 60
    }
}
