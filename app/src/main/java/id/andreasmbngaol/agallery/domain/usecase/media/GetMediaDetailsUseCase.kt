package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/** Fetches detailed metadata (size, dimensions, folder) for a single media item by URI. */
class GetMediaDetailsUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uri: String): MediaDetails? =
        repository.getMediaDetails(uri)
}
