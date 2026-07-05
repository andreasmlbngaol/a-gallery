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
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.usecase.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RefreshMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.RequestWriteAccessUseCase
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

    /**
     * Paksa re-index sumber media. Dipanggil saat izin akses media baru
     * diberikan (grant tidak selalu memicu ContentObserver MediaStore). Satu
     * panggilan menyegarkan grid galeri DAN daftar album karena keduanya
     * berbagi trigger di repository.
     */
    fun refreshMedia() {
        refreshMediaUseCase()
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

    // ---------------------------------------------------------------------
    // Multi-select batch (khusus dipakai di layar detail album; tab Gallery
    // utama tidak mengaktifkan seleksi -> anggota ini tak terpakai di sana).
    // ---------------------------------------------------------------------

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Muat daftar album folder (album cerdas disaring) untuk picker Copy/Move. */
    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = getAlbums().filter { !it.isSmart }.sortedBy { it.name.lowercase() }
        }
    }

    // Batch move butuh SATU consent untuk semua uri (API 30+).
    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    // Ditembakkan saat aksi batch copy/move selesai -> UI keluar dari mode
    // seleksi & refresh grid.
    private val _selectionActionDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val selectionActionDone: SharedFlow<Unit> = _selectionActionDone.asSharedFlow()

    private var pendingWriteAction: (suspend () -> Unit)? = null

    /** Pindahkan banyak item ke Trash (soft-delete). */
    fun moveManyToTrash(items: List<MediaItem>) {
        viewModelScope.launch {
            items.forEach { moveToTrashUseCase(it) }
            _mediaDeleted.emit(Unit)
        }
    }

    /** Hapus permanen banyak item (dialog sistem scoped-storage bila perlu). */
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

    /** Salin banyak item ke album [albumName] (buat file baru; tanpa consent). */
    fun copyManyToAlbum(items: List<MediaItem>, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            items.forEach { runCatching { copyToAlbumUseCase(it, path) } }
            refreshMediaUseCase()
            _selectionActionDone.emit(Unit)
        }
    }

    /**
     * Pindahkan banyak item ke album [albumName] (in-place). Minta SATU consent
     * untuk semua uri; setelah disetujui, pemindahan dijalankan di [doMoveAll].
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
