package id.andreasmbngaol.agallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VM tab Albums. Memuat daftar album (folder) dari MediaStore lewat
 * [GetAlbumsUseCase]. Query dijalankan saat VM dibuat; [refresh] bisa dipanggil
 * ulang, mis. setelah izin media baru diberikan.
 */
class AlbumsViewModel(
    private val getAlbums: GetAlbumsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = AlbumsUiState.Loading
            val albums = getAlbums()
            _state.value = if (albums.isEmpty()) {
                AlbumsUiState.Empty
            } else {
                AlbumsUiState.Content(albums)
            }
        }
    }
}
