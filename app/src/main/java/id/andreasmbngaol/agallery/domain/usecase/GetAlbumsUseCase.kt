package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

class GetAlbumsUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(): List<Album> = repository.getAlbums()
}
