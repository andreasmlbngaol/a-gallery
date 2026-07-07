package id.andreasmbngaol.agallery.domain.usecase.media

import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/** Streams paged media for the given sort order and scope, sourced from the repository. */
class GetMediaPagingUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<PagingData<MediaItem>> =
        repository.getMediaPaging(sortOrder, scope)
}
