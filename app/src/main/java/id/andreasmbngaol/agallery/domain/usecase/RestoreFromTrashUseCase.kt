package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Kembalikan satu item Trash ke gallery -- cukup hapus marker `trashed`.
 * File MediaStore-nya memang tidak pernah benar-benar dihapus saat soft-delete.
 */
class RestoreFromTrashUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long) = repository.restoreFromTrash(mediaId)
}
