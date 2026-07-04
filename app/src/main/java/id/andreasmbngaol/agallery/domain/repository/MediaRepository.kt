package id.andreasmbngaol.agallery.domain.repository

import android.content.IntentSender
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak akses media. Diimplementasikan di layer data.
 * Presentation & domain hanya kenal interface ini.
 *
 * Catatan: PagingData berasal dari androidx.paging (framework). Ini kompromi
 * praktis yang umum di clean-arch Android.
 */
interface MediaRepository {
    /**
     * Stream paging media, diurutkan berdasarkan [sortOrder] & dibatasi
     * [scope] (kamera, semua, video, screenshot, screen recording, favorit,
     * atau satu folder). Setiap ganti scope/sort akan menghasilkan Flow
     * baru; caller (VM) biasanya pakai `flatMapLatest` supaya PagingSource
     * lama otomatis dibatalkan.
     */
    fun getMediaPaging(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): Flow<PagingData<MediaItem>>

    /**
     * Ambil SELURUH media untuk [scope] tertentu (metadata ringan) dalam
     * urutan [sortOrder], sama persis dengan grid. Dipakai viewer supaya bisa
     * buka index mana pun secara instan & geser mulus tanpa bergantung
     * paging. Bitmap tetap di-load lazy per halaman.
     */
    suspend fun getAllMedia(
        sortOrder: GallerySortOrder,
        scope: MediaScope = MediaScope.Camera,
    ): List<MediaItem>

    /** Album folder + album cerdas (Recent/Camera/Videos/Screenshots/dst). */
    suspend fun getAlbums(): List<Album>
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    /** Stream ID media yg difavoritkan (Room). Untuk render status di UI. */
    fun observeFavoriteIds(): Flow<List<Long>>

    suspend fun moveToTrash(mediaId: Long, uri: String)

    /**
     * Stream isi Trash (terbaru dulu). Sumbernya tabel Room `trashed`;
     * bukan MediaStore. Dipakai TrashScreen.
     */
    fun observeTrashItems(): Flow<List<TrashItem>>

    /** Restore satu item dari Trash -> hilangkan marker `trashed`. */
    suspend fun restoreFromTrash(mediaId: Long)

    /**
     * Hapus marker `trashed` untuk [mediaId]. Dipanggil OLEH TrashScreen
     * SETELAH SAF delete-request berhasil (file MediaStore sudah hilang),
     * supaya row Room ikut hilang & tidak jadi ghost record.
     */
    suspend fun finalizePermanentDelete(mediaId: Long)
    suspend fun getMediaDetails(uri: String): MediaDetails?
    suspend fun createDeleteRequest(uris: List<String>): IntentSender?
    suspend fun renameMedia(uriString: String, newDisplayName: String): IntentSender?
    suspend fun moveMediaToAlbum(uriString: String, relativePath: String): IntentSender?
    suspend fun copyMediaToAlbum(
        uriString: String,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    )
}
