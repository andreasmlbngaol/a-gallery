package id.andreasmbngaol.agallery.presentation.albums

import id.andreasmbngaol.agallery.domain.model.album.Album

/**
 * State for the Albums screen.
 *
 * [Content] is now split into pinned (5 smart albums + folders the user pinned,
 * kept in the stored order) and more (the remaining folders, sorted by name).
 */
sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(
        val pinned: List<Album>,
        val more: List<Album>,
        /**
         * Trash pseudo-album. null when the `trashed` table is empty (the Trash
         * row is deliberately hidden so the Albums tab is not noisy for new
         * users). Do not put Trash in [pinned]/[more] -- the Trash row has its
         * own style (a full-width row at the very bottom, not a grid card) &
         * cannot be pinned/unpinned/reordered.
         */
        val trash: Album?,
    ) : AlbumsUiState
}
