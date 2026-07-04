package id.andreasmbngaol.agallery.domain.usecase

import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class GetMediaPagingUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<PagingData<MediaItem>> =
        repository.getMediaPaging(sortOrder, scope)
}
