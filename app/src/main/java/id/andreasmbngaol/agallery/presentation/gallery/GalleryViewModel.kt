package id.andreasmbngaol.agallery.presentation.gallery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import id.andreasmbngaol.agallery.domain.usecase.editing.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.RefreshMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RequestWriteAccessUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ToggleFavoriteUseCase
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
 * Gallery state (used by the main Gallery tab + album detail via a separate keyed VM).
 * Preference source: DataStore via [GetSettingsUseCase].
 */
class GalleryViewModel(
    getSettings: GetSettingsUseCase,
    getMediaPaging: GetMediaPagingUseCase,
    observeFavoriteIds: ObserveFavoriteIdsUseCase,
    private val setSortOrderPref: SetSortOrderUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val deleteMedia: DeleteMediaUseCase,
    private val refreshMediaUseCase: RefreshMediaUseCase,
    private val getAlbums: GetAlbumsUseCase,
    private val copyToAlbumUseCase: CopyMediaToAlbumUseCase,
    private val moveToAlbumUseCase: MoveMediaToAlbumUseCase,
    private val requestWriteAccess: RequestWriteAccessUseCase,
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
     * The media scope being shown. Default [MediaScope.Camera] = the main Gallery
     * tab; the album detail screen uses a separate VM instance and then calls
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

    /**
     * Tracks the last media-permission state so a re-index only happens on a
     * genuine no-access -> access transition (see [onMediaAccessChanged]).
     */
    private var lastMediaAccess = false

    /**
     * Called by the main Gallery tab whenever the media-permission state is
     * (re)evaluated. Only forces a refresh on a real false -> true transition, so
     * simply returning to the tab (e.g. pressing back from the photo viewer) does
     * NOT trigger a full grid refresh -- which previously flashed a reload spinner
     * and re-rendered every thumbnail.
     */
    fun onMediaAccessChanged(hasAccess: Boolean) {
        if (hasAccess && !lastMediaAccess) refreshMediaUseCase()
        lastMediaAccess = hasAccess
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

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Load the folder album list (smart albums filtered out) for the Copy/Move picker. */
    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = getAlbums().filter { !it.isSmart }.sortedBy { it.name.lowercase() }
        }
    }

    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private val _selectionActionDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val selectionActionDone: SharedFlow<Unit> = _selectionActionDone.asSharedFlow()

    private var pendingWriteAction: (suspend () -> Unit)? = null

    /** Move multiple items to Trash (soft-delete). */
    fun moveManyToTrash(items: List<MediaItem>) {
        viewModelScope.launch {
            items.forEach { moveToTrashUseCase(it) }
            _mediaDeleted.emit(Unit)
        }
    }

    /** Permanently delete multiple items (scoped-storage system dialog when needed). */
    fun deleteMany(uris: List<String>) {
        viewModelScope.launch {
            val sender = deleteMedia(uris)
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                _mediaDeleted.emit(Unit)
            }
        }
    }

    /** Copy multiple items to album [albumName] (creates new files; no consent). */
    fun copyManyToAlbum(items: List<MediaItem>, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            items.forEach { runCatching { copyToAlbumUseCase(it, path) } }
            refreshMediaUseCase()
            _selectionActionDone.emit(Unit)
        }
    }

    /**
     * Move multiple items to album [albumName] (in-place). Requests ONE consent
     * for all uris; once approved, the move runs in [doMoveAll].
     */
    fun moveManyToAlbum(items: List<MediaItem>, albumName: String) {
        val path = albumRelativePath(albumName)
        val uris = items.map { it.uri }
        viewModelScope.launch {
            val sender = requestWriteAccess(uris)
            if (sender != null) {
                pendingWriteAction = { doMoveAll(uris, path) }
                _writeRequests.emit(sender)
            } else {
                doMoveAll(uris, path)
            }
        }
    }

    private suspend fun doMoveAll(uris: List<String>, relativePath: String) {
        uris.forEach { runCatching { moveToAlbumUseCase(it, relativePath) } }
        refreshMediaUseCase()
        _mediaDeleted.emit(Unit)
        _selectionActionDone.emit(Unit)
    }

    fun onWriteConsentGranted() {
        val action = pendingWriteAction ?: return
        pendingWriteAction = null
        viewModelScope.launch { action() }
    }

    fun onWriteConsentDenied() {
        pendingWriteAction = null
    }

    private fun albumRelativePath(name: String): String {
        val clean = name.trim().trim('/')
        return "DCIM/$clean/"
    }
}
