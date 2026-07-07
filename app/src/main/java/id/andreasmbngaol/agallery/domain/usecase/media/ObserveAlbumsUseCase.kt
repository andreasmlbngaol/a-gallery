package id.andreasmbngaol.agallery.domain.usecase.media

import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Reactive stream of the album list (folder + smart). Updates automatically when:
 * - MediaStore changes (new or deleted photos/videos) via ContentObserver,
 * - the favorites list changes (the Favorites album appears/disappears instantly),
 * - the Trash list changes (an album emptied into Trash disappears too),
 * - a cover override is changed via "Set as Cover",
 * - a manual refresh is triggered (e.g. after media permission is granted).
 */
class ObserveAlbumsUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Album>> = repository.observeAlbums()
}
