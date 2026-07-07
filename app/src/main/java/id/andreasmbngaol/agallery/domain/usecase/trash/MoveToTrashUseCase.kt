package id.andreasmbngaol.agallery.domain.usecase.trash

import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Moves a media item to the app-level Trash (soft-delete, 30-day retention). The
 * file stays on device so it can be restored later. The type and duration are
 * stored too so the Trash screen can show a video badge without re-querying.
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
