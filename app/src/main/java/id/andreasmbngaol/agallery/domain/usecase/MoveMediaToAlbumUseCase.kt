package id.andreasmbngaol.agallery.domain.usecase

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Move a media item into the folder at relativePath (e.g. "DCIM/Trips/") by
 * updating its RELATIVE_PATH — an in-place move without copying bytes (API 30+).
 *
 * Returns an [IntentSender] when write consent is required; on success the
 * caller re-invokes this use case to commit. Returns null when done.
 */
class MoveMediaToAlbumUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uriString: String, relativePath: String): IntentSender? =
        repository.moveMediaToAlbum(uriString, relativePath)
}
