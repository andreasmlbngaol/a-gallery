package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stream reaktif daftar album (folder + cerdas). Ter-update otomatis saat:
 * - MediaStore berubah (foto/video baru, terhapus) via ContentObserver,
 * - daftar favorit berubah (album Favorites muncul/hilang seketika),
 * - daftar Trash berubah (album yang isinya habis ke-trash ikut hilang),
 * - cover override diubah lewat "Set as Cover",
 * - refresh manual dipicu (mis. setelah izin media diberikan).
 */
class ObserveAlbumsUseCase(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Album>> = repository.observeAlbums()
}
