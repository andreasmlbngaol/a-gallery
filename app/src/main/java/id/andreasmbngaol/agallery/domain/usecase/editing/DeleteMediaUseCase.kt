package id.andreasmbngaol.agallery.domain.usecase.editing

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Requests deletion of media (photos/videos).
 *
 * Returns an [IntentSender] that MUST be launched via ActivityResult because
 * scoped storage requires user confirmation on API 30+ (the system shows the
 * dialog and performs the deletion itself). A null return means the file was
 * deleted directly (API 29 for files owned by the app), so the UI only needs to
 * refresh.
 */
class DeleteMediaUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): IntentSender? =
        repository.createDeleteRequest(uris)
}
