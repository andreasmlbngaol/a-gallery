package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Forces a refresh of the media sources (grid paging + album list). Used mainly
 * after the user grants media access, since granting permission does not always
 * trigger a MediaStore ContentObserver notification.
 */
class RefreshMediaUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke() = repository.refreshMedia()
}
