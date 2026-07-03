package id.andreasmbngaol.agallery.presentation.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.usecase.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaDetailsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Menyediakan daftar media untuk viewer.
 *
 * ## Kenapa daftar penuh, bukan paging?
 *
 * Viewer perlu membuka index mana pun (posisi tap di grid) SECARA INSTAN dan
 * bisa digeser ke kiri/kanan tanpa jeda. Pendekatan paging + jumping ternyata
 * flaky untuk kasus ini: lompat jauh sering "loading terus / lama / kadang
 * gagal, back lalu tekan lagi baru berhasil". Karena MediaItem hanya metadata
 * ringan (bukan bitmap), memuat SELURUH daftar sekali cukup murah; bitmap tetap
 * di-load lazy per halaman oleh Coil.
 *
 * ## Sort order
 *
 * Viewer WAJIB memakai sort order yang sama dengan grid. Kalau tidak,
 * `initialIndex` (posisi tap di grid) akan menunjuk foto yang salah. Order
 * diteruskan lewat nav arg `Screen.PhotoViewer.sortOrder` -> [setSortOrder].
 */
class PhotoViewerViewModel(
    getAllMedia: GetAllMediaUseCase,
    private val getMediaDetails: GetMediaDetailsUseCase,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(GallerySortOrder.DateDesc)

    /**
     * Daftar media untuk viewer. `null` = masih dimuat (tampilkan loading);
     * list kosong = tidak ada media; selain itu daftar siap dipakai pager.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<List<MediaItem>?> = _sortOrder
        .flatMapLatest { order -> flow { emit(getAllMedia(order)) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    fun setSortOrder(order: GallerySortOrder) {
        if (_sortOrder.value != order) _sortOrder.value = order
    }

    /** Muat metadata detail (ukuran, dimensi, folder) satu media by URI. */
    suspend fun loadDetails(uri: String): MediaDetails? = getMediaDetails(uri)
}
