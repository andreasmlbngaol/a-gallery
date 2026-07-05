package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Paksa refresh sumber media (grid paging + daftar album). Dipakai terutama
 * setelah user memberi izin akses media, karena pemberian izin tidak selalu
 * memicu notifikasi ContentObserver dari MediaStore.
 */
class RefreshMediaUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke() = repository.refreshMedia()
}
