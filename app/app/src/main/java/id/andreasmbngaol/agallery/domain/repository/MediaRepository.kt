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

    /**
     * Ambil SELURUH media kamera (metadata ringan) dalam urutan [sortOrder],
     * sama persis dengan grid. Dipakai viewer supaya bisa buka index mana pun
     * secara instan & geser mulus tanpa bergantung paging (yang bisa "loading
     * terus" saat lompat jauh). Bitmap tetap di-load lazy per halaman.
     */
    suspend fun getAllMedia(sortOrder: GallerySortOrder): List<MediaItem>

    suspend fun getAlbums(): List<Album>
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    /** Stream ID media yg difavoritkan (Room). Untuk render status di UI. */
    fun observeFavoriteIds(): Flow<List<Long>>

    /**
     * Pindahkan media ke Trash (soft-delete). Item disembunyikan dari galeri
     * utama tapi filenya BELUM dihapus, jadi bisa dipulihkan dalam 30 hari.
     */
    suspend fun moveToTrash(mediaId: Long, uri: String)

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

    /**
     * Rename a media item's display name. Returns an [IntentSender] when write
     * consent is required (scoped storage); null when the rename succeeded.
     */
    suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender?

    /**
     * Move a media item into the folder at [relativePath] by updating its
     * RELATIVE_PATH. Returns an [IntentSender] when write consent is required;
     * null when the move succeeded.
     */
    suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender?

    /**
     * Copy a media item into the folder at [relativePath] as a new file.
     */
    suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    )
}
