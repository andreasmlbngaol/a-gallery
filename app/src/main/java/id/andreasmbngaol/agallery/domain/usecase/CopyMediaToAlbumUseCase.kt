package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaType
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Copy a media item into the folder at [relativePath] as a brand-new file.
 * Creates a new MediaStore entry and streams the bytes over — no write consent
 * needed because the app owns the newly created file.
 */
class CopyMediaToAlbumUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(item: MediaItem, relativePath: String) =
        repository.copyMediaToAlbum(
            uriString = item.uri,
            relativePath = relativePath,
            displayName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.type == MediaType.VIDEO,
        )
}
