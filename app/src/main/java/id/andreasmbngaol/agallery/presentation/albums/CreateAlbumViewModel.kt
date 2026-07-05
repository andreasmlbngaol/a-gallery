package id.andreasmbngaol.agallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.usecase.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RefreshMediaUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateAlbumViewModel(
    private val getAlbums: GetAlbumsUseCase,
    private val getAllMedia: GetAllMediaUseCase,
    private val copyToAlbumUseCase: CopyMediaToAlbumUseCase,
    private val refreshMediaUseCase: RefreshMediaUseCase,
    getSettings: GetSettingsUseCase,
) : ViewModel() {

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _albumMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val albumMedia: StateFlow<List<MediaItem>> = _albumMedia.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    // Gaya komponen (Solid/Frosted/Glass) & efek tepi -> top bar, FAB, dan scrim
    // dibuat seragam dengan layar lain (Trash/Gallery).
    val componentStyle: StateFlow<ComponentStyle?> = getSettings()
        .map { it.componentStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val edgeEffectMode: StateFlow<EdgeEffectMode?> = getSettings()
        .map { it.edgeEffectMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    /** Semua album, diurutkan berdasarkan nama (bukan urutan acak). */
    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = getAlbums().sortedBy { it.name.lowercase() }
        }
    }

    fun openAlbum(scope: MediaScope) {
        viewModelScope.launch {
            _albumMedia.value = getAllMedia(GallerySortOrder.DateDesc, scope)
        }
    }

    fun closeAlbum() {
        _albumMedia.value = emptyList()
    }

    /** Buat album baru dengan menyalin (copy) item terpilih ke DCIM/<nama>/. */
    fun create(name: String, items: List<MediaItem>) {
        if (items.isEmpty() || _creating.value) return
        val path = albumRelativePath(name)
        viewModelScope.launch {
            _creating.value = true
            items.forEach { item -> runCatching { copyToAlbumUseCase(item, path) } }
            refreshMediaUseCase()
            _creating.value = false
            _done.emit(Unit)
        }
    }

    private fun albumRelativePath(name: String): String {
        val clean = name.trim().trim('/')
        return "DCIM/$clean/"
    }
}
