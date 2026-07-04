package id.andreasmbngaol.agallery.presentation.gallery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveFavoriteIdsUseCase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State galeri (dipakai tab Gallery utama + detail album via key VM terpisah).
 * Sumber preferensi: DataStore lewat [GetSettingsUseCase].
 */
class GalleryViewModel(
    getSettings: GetSettingsUseCase,
    getMediaPaging: GetMediaPagingUseCase,
    observeFavoriteIds: ObserveFavoriteIdsUseCase,
    private val setSortOrderPref: SetSortOrderUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
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

    val edgeEffectMode: StateFlow<EdgeEffectMode?> = settings
        .map { it.edgeEffectMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val componentStyle: StateFlow<ComponentStyle?> = settings
        .map { it.componentStyle }
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

    val performanceMode: StateFlow<PerformanceMode> = settings
        .map { it.performanceMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PerformanceMode.BALANCED,
        )

    val favoriteIds: StateFlow<Set<Long>> = observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

    /**
     * Scope media yang ditampilkan. Default [MediaScope.Camera] = tab Gallery
     * utama; layar detail album memakai instance VM terpisah lalu memanggil
     * [setScope].
     */
    private val _scope = MutableStateFlow<MediaScope>(MediaScope.Camera)

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: Flow<PagingData<MediaItem>> =
        combine(
            settings.map { it.sortOrder }.distinctUntilChanged(),
            _scope,
        ) { order, scope -> order to scope }
            .flatMapLatest { (order, scope) -> getMediaPaging(order, scope) }
            .cachedIn(viewModelScope)

    fun setScope(scope: MediaScope) {
        if (_scope.value != scope) _scope.value = scope
    }

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

    private val _previewItem = MutableStateFlow<MediaItem?>(null)
    val previewItem: StateFlow<MediaItem?> = _previewItem.asStateFlow()

    fun showPreview(item: MediaItem) {
        _previewItem.value = item
    }

    fun dismissPreview() {
        _previewItem.value = null
    }

    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()

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

    fun moveToTrash(item: MediaItem) {
        viewModelScope.launch {
            moveToTrashUseCase(item)
            dismissPreview()
        }
    }
}
