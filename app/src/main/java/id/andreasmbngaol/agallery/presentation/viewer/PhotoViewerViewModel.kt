package id.andreasmbngaol.agallery.presentation.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Menyediakan aliran media untuk viewer, memakai sumber paging yang sama
 * dengan Gallery.
 *
 * ## Sort order
 *
 * Viewer WAJIB memakai sort order yang sama dengan grid. Kalau tidak,
 * `initialIndex` (posisi tap di grid) akan menunjuk foto yang salah di stream
 * viewer. Order diteruskan lewat nav arg `Screen.PhotoViewer.sortOrder` ->
 * [setSortOrder] -> `flatMapLatest` (pola sama seperti GalleryViewModel).
 */
class PhotoViewerViewModel(
    getMediaPaging: GetMediaPagingUseCase,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(GallerySortOrder.DateDesc)

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: Flow<PagingData<MediaItem>> = _sortOrder
        .flatMapLatest { order -> getMediaPaging(order) }
        .cachedIn(viewModelScope)

    fun setSortOrder(order: GallerySortOrder) {
        if (_sortOrder.value != order) _sortOrder.value = order
    }
}
