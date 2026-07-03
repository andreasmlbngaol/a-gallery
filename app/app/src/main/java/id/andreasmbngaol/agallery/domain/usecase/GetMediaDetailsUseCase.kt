package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/** Ambil metadata detail (ukuran, dimensi, folder) untuk satu media by URI. */
class GetMediaDetailsUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uri: String): MediaDetails? =
        repository.getMediaDetails(uri)
}
