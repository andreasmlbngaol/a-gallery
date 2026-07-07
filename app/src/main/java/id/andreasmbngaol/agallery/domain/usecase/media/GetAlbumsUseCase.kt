package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/** Returns the current list of albums (folder + smart) as a one-shot snapshot. */
class GetAlbumsUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(): List<Album> = repository.getAlbums()
}
