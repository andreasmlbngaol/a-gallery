package id.andreasmbngaol.agallery.domain.usecase.trash

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * After the SAF delete request succeeds (the MediaStore file is gone), call this
 * to clean up the Room `trashed` row so it does not become a ghost record.
 *
 * The separation from [MoveToTrashUseCase] is intentional: MoveToTrash is the
 * soft-delete, while FinalizePermanentDelete is the follow-up after the
 * permanent SAF confirmation.
 */
class FinalizePermanentDeleteUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long) = repository.finalizePermanentDelete(mediaId)
}
