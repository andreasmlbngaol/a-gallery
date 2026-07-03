package id.andreasmbngaol.agallery.presentation.albums

import id.andreasmbngaol.agallery.domain.model.Album

/** State layar Albums: sedang memuat, kosong, atau berisi daftar album. */
sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(val albums: List<Album>) : AlbumsUiState
}
