package id.andreasmbngaol.agallery.domain.usecase

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Rename a media item's display name.
 *
 * Returns an [IntentSender] when the system needs the user to grant write
 * access (scoped storage) before the change can be applied. In that case the
 * caller must launch it and, on success, invoke this use case again to commit
 * the rename. Returns null when the rename already succeeded.
 */
class RenameMediaUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uriString: String, newDisplayName: String): IntentSender? =
        repository.renameMedia(uriString, newDisplayName)
}
