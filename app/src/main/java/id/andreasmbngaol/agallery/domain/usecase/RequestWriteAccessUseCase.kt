package id.andreasmbngaol.agallery.domain.usecase

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Minta SATU write consent (API 30+) untuk sekumpulan [uris] sekaligus.
 *
 * Dipakai oleh aksi batch "Move to album": alih-alih meminta izin per item,
 * kita minta satu [IntentSender] untuk semua uri. Setelah user menyetujui,
 * pemanggil menjalankan pemindahan tiap item tanpa dialog tambahan.
 *
 * Mengembalikan null bila tak perlu consent (mis. app punya All-files access).
 */
class RequestWriteAccessUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): IntentSender? =
        repository.createWriteRequest(uris)
}
