package id.andreasmbngaol.agallery.domain.usecase.trash

import id.andreasmbngaol.agallery.domain.model.trash.TrashItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the Trash contents (Room `trashed`, newest first). Used by the Trash
 * screen and the count badge in the Trash section of the Albums tab.
 */
class ObserveTrashItemsUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<TrashItem>> = repository.observeTrashItems()
}
