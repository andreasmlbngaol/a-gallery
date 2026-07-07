package id.andreasmbngaol.agallery.domain.usecase.favorite

import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the set of favorited media IDs (local Room). Used by the UI to render
 * the favorite state without joining into the paging stream.
 */
class ObserveFavoriteIdsUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Long>> = repository.observeFavoriteIds()
}
