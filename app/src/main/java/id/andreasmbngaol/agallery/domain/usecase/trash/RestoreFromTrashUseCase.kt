package id.andreasmbngaol.agallery.domain.usecase.trash

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Restores a single Trash item back to the gallery -- it just clears the
 * `trashed` marker. The MediaStore file is never actually deleted during
 * soft-delete.
 */
class RestoreFromTrashUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long) = repository.restoreFromTrash(mediaId)
}
