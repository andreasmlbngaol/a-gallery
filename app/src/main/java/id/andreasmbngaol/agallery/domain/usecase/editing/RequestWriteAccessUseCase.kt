package id.andreasmbngaol.agallery.domain.usecase.editing

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Requests a SINGLE write consent (API 30+) for a batch of URIs at once.
 *
 * Used by the batch "Move to album" action: instead of asking for permission per
 * item, it requests one [IntentSender] for all URIs. Once the user approves, the
 * caller performs each move without additional dialogs.
 *
 * Returns null when consent is not needed (e.g. the app has All-files access).
 */
class RequestWriteAccessUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): IntentSender? =
        repository.createWriteRequest(uris)
}
