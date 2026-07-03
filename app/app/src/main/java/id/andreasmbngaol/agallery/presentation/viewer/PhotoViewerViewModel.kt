package id.andreasmbngaol.agallery.presentation.viewer

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.usecase.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RenameMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel untuk [PhotoViewerScreen]. Selain memuat daftar media & detail,
 * kini juga menghandle semua aksi standar viewer: favorite, trash, delete,
 * rename, move/copy ke album.
 *
 * Alur izin scoped-storage:
 * - Delete non-owned -> [deleteRequests] (system delete dialog). Setelah
 *   RESULT_OK, UI memanggil [refresh].
 * - Rename/Move non-owned -> [writeRequests] (system write dialog). Setelah
 *   RESULT_OK, UI memanggil [onWriteConsentGranted], yang menjalankan ulang
 *   aksi tertunda (update kini sukses).
 */
class PhotoViewerViewModel(
    getAllMedia: GetAllMediaUseCase,
    observeFavoriteIds: ObserveFavoriteIdsUseCase,
    getSettings: GetSettingsUseCase,
    private val getAlbums: GetAlbumsUseCase,
    private val getMediaDetails: GetMediaDetailsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val deleteMedia: DeleteMediaUseCase,
    private val renameMediaUseCase: RenameMediaUseCase,
    private val moveToAlbumUseCase: MoveMediaToAlbumUseCase,
    private val copyToAlbumUseCase: CopyMediaToAlbumUseCase,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(GallerySortOrder.DateDesc)
    // Dinaikkan setiap kali daftar perlu dimuat ulang (setelah trash/delete/
    // rename/move) supaya viewer tetap sinkron dgn kondisi terbaru MediaStore.
    private val _refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<List<MediaItem>?> =
        combine(_sortOrder, _refresh) { order, _ -> order }
            .flatMapLatest { order -> flow { emit(getAllMedia(order)) } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = null,
            )

    val favoriteIds: StateFlow<Set<Long>> = observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptySet(),
        )

    /** Mode edge-effect (setting) -> viewer pakai utk on/off liquid glass. */
    val edgeEffectMode: StateFlow<EdgeEffectMode?> = getSettings()
        .map { it.edgeEffectMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    /** Gaya komponen UI (Solid/Frosted/Glass). null = default cerdas (di UI). */
    val componentStyle: StateFlow<ComponentStyle?> = getSettings()
        .map { it.componentStyle }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()

    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Aksi yg dijalankan ulang setelah user memberi izin tulis (rename/move).
    private var pendingWriteAction: (suspend () -> Unit)? = null

    fun setSortOrder(order: GallerySortOrder) {
        if (_sortOrder.value != order) _sortOrder.value = order
    }

    fun refresh() {
        _refresh.value += 1
    }

    suspend fun loadDetails(uri: String): MediaDetails? = getMediaDetails(uri)

    fun loadAlbums() {
        viewModelScope.launch { _albums.value = getAlbums() }
    }

    fun onToggleFavorite(mediaId: Long, isFavorite: Boolean) {
        viewModelScope.launch { toggleFavorite(mediaId, isFavorite) }
    }

    fun moveToTrash(item: MediaItem) {
        viewModelScope.launch {
            moveToTrashUseCase(item)
            refresh()
            _messages.emit("Moved to Trash")
        }
    }

    fun deletePhoto(uri: String) {
        viewModelScope.launch {
            val sender = deleteMedia(listOf(uri))
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                refresh()
                _messages.emit("Deleted")
            }
        }
    }

    fun rename(uri: String, newName: String) {
        viewModelScope.launch {
            val sender = renameMediaUseCase(uri, newName)
            if (sender != null) {
                pendingWriteAction = {
                    renameMediaUseCase(uri, newName)
                    refresh()
                    _messages.emit("Renamed")
                }
                _writeRequests.emit(sender)
            } else {
                refresh()
                _messages.emit("Renamed")
            }
        }
    }

    fun moveToAlbum(uri: String, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            val sender = moveToAlbumUseCase(uri, path)
            if (sender != null) {
                pendingWriteAction = {
                    moveToAlbumUseCase(uri, path)
                    refresh()
                    _messages.emit("Moved to $albumName")
                }
                _writeRequests.emit(sender)
            } else {
                refresh()
                _messages.emit("Moved to $albumName")
            }
        }
    }

    fun copyToAlbum(item: MediaItem, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            copyToAlbumUseCase(item, path)
            _messages.emit("Copied to $albumName")
        }
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
