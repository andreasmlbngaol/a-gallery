package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Setelah SAF delete-request berhasil (file MediaStore hilang), panggil ini
 * untuk membersihkan row Room `trashed` supaya tidak menjadi ghost record.
 *
 * Pemisahan dari [MoveToTrashUseCase] disengaja: MoveToTrash = soft-delete,
 * FinalizePermanentDelete = follow-up setelah confirm SAF permanen.
 */
class FinalizePermanentDeleteUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long) = repository.finalizePermanentDelete(mediaId)
}
