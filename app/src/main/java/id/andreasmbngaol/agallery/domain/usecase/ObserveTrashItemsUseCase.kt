package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.TrashItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stream isi Trash (Room `trashed`, terbaru dulu). Untuk layar Trash &
 * badge jumlah di section Trash di tab Albums.
 */
class ObserveTrashItemsUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<TrashItem>> = repository.observeTrashItems()
}
