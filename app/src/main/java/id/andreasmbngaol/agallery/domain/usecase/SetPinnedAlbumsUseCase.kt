package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/**
 * Simpan urutan album yang di-pin di tab Albums. Urutan list = urutan tampil
 * (kiri->kanan, atas->bawah). Dipanggil saat user pin/unpin lewat overlay
 * long-press atau setelah drag-reorder di mode Reorder.
 */
class SetPinnedAlbumsUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(keys: List<String>) {
        repository.setPinnedAlbumKeys(keys)
    }
}
