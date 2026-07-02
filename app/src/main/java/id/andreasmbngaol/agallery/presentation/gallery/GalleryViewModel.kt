package id.andreasmbngaol.agallery.presentation.gallery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State galeri. Semua preferensi (sort, edge effect, jumlah kolom) sekarang
 * bersumber dari DataStore lewat [GetSettingsUseCase] sebagai SATU sumber
 * kebenaran, sehingga:
 * - Pilihan di layar Settings langsung berpengaruh ke galeri (reaktif).
 * - Sort order TETAP tersimpan walau aplikasi ditutup (bukan cuma
 *   rememberSaveable yang hilang saat proses di-kill).
 *
 * Ganti [sortOrder] akan re-trigger paging lewat `flatMapLatest`; PagingSource
 * lama otomatis di-cancel.
 */
class GalleryViewModel(
    getSettings: GetSettingsUseCase,
    getMediaPaging: GetMediaPagingUseCase,
    private val setSortOrderPref: SetSortOrderUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val deleteMedia: DeleteMediaUseCase,
) : ViewModel() {

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    val sortOrder: StateFlow<GallerySortOrder> = settings
        .map { it.sortOrder }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GallerySortOrder.DateDesc,
        )

    /** Pilihan mentah user (nullable = default cerdas, di-resolve di UI). */
    val edgeEffectMode: StateFlow<EdgeEffectMode?> = settings
        .map { it.edgeEffectMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val gridColumns: StateFlow<Int> = settings
        .map { it.gridColumns }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_GRID_COLUMNS,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: Flow<PagingData<MediaItem>> = settings
        .map { it.sortOrder }
        .distinctUntilChanged()
        .flatMapLatest { order -> getMediaPaging(order) }
        .cachedIn(viewModelScope)

    fun setSortOrder(order: GallerySortOrder) {
        if (sortOrder.value == order) return
        viewModelScope.launch { setSortOrderPref(order) }
    }

    fun toggleSortOrder() {
        val next = when (sortOrder.value) {
            GallerySortOrder.DateDesc -> GallerySortOrder.DateAsc
            GallerySortOrder.DateAsc -> GallerySortOrder.DateDesc
        }
        viewModelScope.launch { setSortOrderPref(next) }
    }

    /**
     * Foto yang sedang di-preview (long-press). Di-hoist ke VM (bukan state
     * lokal screen) supaya HomeTabsScreen ikut tahu preview aktif → untuk
     * mengunci swipe antar-tab & menutup floating nav bar.
     */
    private val _previewItem = MutableStateFlow<MediaItem?>(null)
    val previewItem: StateFlow<MediaItem?> = _previewItem.asStateFlow()

    fun showPreview(item: MediaItem) {
        _previewItem.value = item
    }

    fun dismissPreview() {
        _previewItem.value = null
    }

    /**
     * Permintaan hapus yang butuh konfirmasi sistem (scoped storage, API 30+).
     * UI meng-collect ini lalu meluncurkannya lewat ActivityResult IntentSender.
     */
    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()

    /** Sinyal media sudah langsung terhapus (API 29 tanpa dialog) → UI refresh. */
    private val _mediaDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaDeleted: SharedFlow<Unit> = _mediaDeleted.asSharedFlow()

    fun deletePhoto(uri: String) {
        viewModelScope.launch {
            val sender = deleteMedia(listOf(uri))
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                _mediaDeleted.emit(Unit)
            }
        }
    }

    fun onToggleFavorite(mediaId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            toggleFavorite(mediaId, isFavorite)
        }
    }
}
