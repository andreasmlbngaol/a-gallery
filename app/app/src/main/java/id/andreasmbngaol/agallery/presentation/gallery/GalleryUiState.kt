package id.andreasmbngaol.agallery.presentation.gallery

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data object PermissionRequired : GalleryUiState
    data object Ready : GalleryUiState
}
