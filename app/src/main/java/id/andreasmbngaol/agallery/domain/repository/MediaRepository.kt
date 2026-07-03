package id.andreasmbngaol.agallery.domain.repository

import android.content.IntentSender
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak akses media. Diimplementasikan di layer data.
 * Presentation & domain hanya kenal interface ini.
 *
 * Catatan: PagingData berasal dari androidx.paging (framework). Ini kompromi
 * praktis yang umum di clean-arch Android. Kalau mau domain 100% murni,
 * bungkus paging di layer data.
 */
interface MediaRepository {
    /**
     * Stream paging media, diurutkan berdasarkan [sortOrder]. Setiap ganti
     * sortOrder akan menghasilkan Flow terpisah — caller (VM) biasanya
     * pakai `flatMapLatest` supaya PagingSource lama otomatis dibatalkan.
     */
    fun getMediaPaging(sortOrder: GallerySortOrder): Flow<PagingData<MediaItem>>

    suspend fun getAlbums(): List<Album>
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    /**
     * Ambil metadata detail (ukuran, dimensi, folder) untuk satu media by
     * [uri]. Return null kalau item tak ditemukan. Dipakai panel detail viewer.
     */
    suspend fun getMediaDetails(uri: String): MediaDetails?

    /**
     * Bangun permintaan hapus untuk daftar [uris] (string URI MediaStore).
     * Return null kalau file sudah terhapus langsung tanpa perlu izin user;
     * selain itu kembalikan [IntentSender] yang harus di-launch UI.
     *
     * Catatan: [IntentSender] tipe android.* — kompromi praktis yang sama
     * seperti PagingData, supaya alur scoped-storage (ActivityResult) jalan
     * tanpa abstraksi tambahan.
     */
    suspend fun createDeleteRequest(uris: List<String>): IntentSender?
}
