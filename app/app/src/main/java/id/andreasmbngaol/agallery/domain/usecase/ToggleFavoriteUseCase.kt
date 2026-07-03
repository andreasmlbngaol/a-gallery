package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

class ToggleFavoriteUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long, isFavorite: Boolean) =
        repository.setFavorite(mediaId, isFavorite)
}
