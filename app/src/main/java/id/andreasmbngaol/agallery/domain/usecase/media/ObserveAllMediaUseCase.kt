package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Reactive stream of all media for the viewer. Mirrors [GetAllMediaUseCase] but
 * updates automatically when MediaStore or the Trash set changes, so the viewer
 * never shows blank pages for trashed/deleted items and stays aligned with the
 * grid ordering.
 */
class ObserveAllMediaUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<List<MediaItem>> = repository.observeAllMedia(sortOrder, scope)
}
