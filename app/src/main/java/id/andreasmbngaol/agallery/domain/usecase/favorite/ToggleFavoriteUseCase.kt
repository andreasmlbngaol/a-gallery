package id.andreasmbngaol.agallery.domain.usecase.favorite

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/** Sets or clears the favorite flag for a media item. */
class ToggleFavoriteUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long, isFavorite: Boolean) =
        repository.setFavorite(mediaId, isFavorite)
}
