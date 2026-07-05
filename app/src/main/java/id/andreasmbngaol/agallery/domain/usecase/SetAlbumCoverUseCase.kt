package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Pilih [mediaId] sebagai cover album [albumKey] (aksi "Set as Cover" di
 * viewer). Disimpan di Room supaya reaktif & persisten.
 */
class SetAlbumCoverUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(albumKey: String, mediaId: Long) =
        repository.setAlbumCover(albumKey, mediaId)
}
