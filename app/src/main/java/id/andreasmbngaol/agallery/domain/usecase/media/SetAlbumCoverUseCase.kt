package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Sets mediaId as the cover for albumKey (the "Set as Cover" action in the
 * viewer). Stored in Room so it is reactive and persistent.
 */
class SetAlbumCoverUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(albumKey: String, mediaId: Long) =
        repository.setAlbumCover(albumKey, mediaId)
}
