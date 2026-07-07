package id.andreasmbngaol.agallery.domain.repository

import android.content.IntentSender
import androidx.paging.PagingData
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.ImageFormat
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.model.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.MetadataRemovalOutcome
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

    /**
     * Versi REAKTIF dari [getAlbums]. Ter-update otomatis saat MediaStore
     * berubah, favorit/Trash berubah, cover override berubah, atau [refreshMedia]
     * dipicu. Ini inti "auto re-indexing" album tanpa perlu tutup-buka app.
     */
    fun observeAlbums(): Flow<List<Album>>

    /** Simpan pilihan cover album ("Set as Cover") -> reaktif via [observeAlbums]. */
    suspend fun setAlbumCover(albumKey: String, mediaId: Long)

    /**
     * Paksa refresh sumber media (grid paging + [observeAlbums]). Dipakai
     * terutama setelah user memberi izin akses media, karena grant tidak
     * selalu memicu notifikasi ContentObserver MediaStore.
     */
    fun refreshMedia()

    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    /** Stream ID media yg difavoritkan (Room). Untuk render status di UI. */
    fun observeFavoriteIds(): Flow<List<Long>>

    suspend fun moveToTrash(
        mediaId: Long,
        uri: String,
        isVideo: Boolean,
        durationMs: Long,
    )

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

    /**
     * Auto-purge retensi Trash: kembalikan URI item yg umurnya melebihi
     * [retentionDays] (mis. 30) supaya caller bisa membangun SAF delete-request
     * utk menghapus file-nya. Marker Room baru dihapus setelah delete disetujui
     * (via [finalizePermanentDelete]).
     */
    suspend fun purgeExpiredTrash(retentionDays: Int = 30): List<String>

    /**
     * Purge LANGSUNG (tanpa dialog) item Trash yg umurnya > [retentionDays].
     * Hanya jalan bila app punya All-files access; kalau tidak, return 0.
     * Dipakai TrashPurgeWorker utk auto-purge 30 hari di background.
     * Mengembalikan jumlah item yg benar-benar terhapus.
     */
    suspend fun autoPurgeExpiredDirectly(retentionDays: Int = 30): Int
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

    /**
     * Bangun satu write-request (API 30+) untuk SEKUMPULAN uri sekaligus,
     * dipakai batch move di album detail supaya user cukup menyetujui SATU
     * dialog consent untuk semua item. Null bila tak perlu consent (All-files
     * access) atau perangkat < API 30.
     */
    suspend fun createWriteRequest(uris: List<String>): IntentSender?

    /**
     * Buang metadata terpilih ([categories]) dari sebuah foto. Kalau
     * [saveAsCopy] true, hasil disimpan sebagai salinan bersih (asli utuh);
     * kalau false, file asli ditimpa. Lihat [MetadataRemovalOutcome] utk hasil
     * (termasuk NeedsConsent bila file bukan milik app).
     */
    suspend fun removeMetadata(
        uriString: String,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ): MetadataRemovalOutcome

    /**
     * Konversi foto [uriString] ke [target] format ([quality] 1..100 utk lossy;
     * PNG mengabaikannya). Selalu menghasilkan file baru di folder yg sama;
     * penghapusan asli diputuskan pemanggil. Lihat [ConversionOutcome].
     */
    suspend fun convertImageFormat(
        uriString: String,
        target: ImageFormat,
        quality: Int,
    ): ConversionOutcome
}
