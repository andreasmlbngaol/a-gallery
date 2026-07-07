package id.andreasmbngaol.agallery.presentation.albums

import id.andreasmbngaol.agallery.domain.model.album.Album

/**
 * State layar Albums.
 *
 * [Content] sekarang dipecah jadi pinned (5 album cerdas + folder yang
 * di-pin user, urutan tetap seperti yang disimpan) dan more (sisa folder,
 * disortir menurut nama).
 */
sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(
        val pinned: List<Album>,
        val more: List<Album>,
        /**
         * Trash pseudo-album. null saat tabel `trashed` kosong (baris Trash
         * sengaja disembunyikan supaya tab Albums tidak berisik untuk user
         * baru). Jangan taruh Trash di [pinned]/[more] -- baris Trash punya
         * gaya sendiri (row full-width di paling bawah, bukan kartu grid)
         * & tidak boleh di-pin/unpin/reorder.
         */
        val trash: Album?,
    ) : AlbumsUiState
}
