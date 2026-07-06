package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Ambil seluruh media (metadata ringan) dalam urutan tertentu untuk scope
 * tertentu. Dipakai viewer supaya bisa buka index mana pun secara instan
 * & geser mulus tanpa bergantung paging.
 */
class GetAllMediaUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): List<MediaItem> =
        repository.getAllMedia(sortOrder, scope)
}
