package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Ambil seluruh media kamera (metadata ringan) dalam urutan tertentu. Dipakai
 * viewer supaya bisa buka index mana pun secara instan & geser mulus tanpa
 * bergantung paging.
 */
class GetAllMediaUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(sortOrder: GallerySortOrder): List<MediaItem> =
        repository.getAllMedia(sortOrder)
}
