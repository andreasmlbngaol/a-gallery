package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaType
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Move a media item to the app-level Trash (soft-delete, 30-day retention).
 * The file stays on device so it can be restored later. Tipe & durasi ikut
 * disimpan supaya layar Trash bisa menampilkan badge video tanpa query ulang.
 */
class MoveToTrashUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(item: MediaItem) =
        repository.moveToTrash(
            mediaId = item.id,
            uri = item.uri,
            isVideo = item.type == MediaType.VIDEO,
            durationMs = item.durationMs,
        )
}
