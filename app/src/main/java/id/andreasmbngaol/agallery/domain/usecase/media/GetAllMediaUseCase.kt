package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Returns all media (lightweight metadata) in the given order for the given
 * scope. Used by the viewer so it can open any index instantly and swipe
 * smoothly without depending on paging.
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
