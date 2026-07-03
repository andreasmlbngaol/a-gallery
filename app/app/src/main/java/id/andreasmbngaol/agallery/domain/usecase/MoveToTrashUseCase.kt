package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Move a media item to the app-level Trash (soft-delete, 30-day retention).
 * The file stays on device so it can be restored later.
 */
class MoveToTrashUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(item: MediaItem) =
        repository.moveToTrash(item.id, item.uri)
}
