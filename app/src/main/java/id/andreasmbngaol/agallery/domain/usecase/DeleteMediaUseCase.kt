package id.andreasmbngaol.agallery.domain.usecase

import android.content.IntentSender
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Minta penghapusan media (foto/video).
 *
 * Mengembalikan [IntentSender] yang HARUS di-launch lewat ActivityResult
 * karena scoped storage butuh konfirmasi user di API 30+ (sistem sendiri yang
 * menampilkan dialog + menghapus). Return null artinya file sudah langsung
 * terhapus (API 29 untuk file milik app sendiri) sehingga UI cukup refresh.
 */
class DeleteMediaUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): IntentSender? =
        repository.createDeleteRequest(uris)
}
