package id.andreasmbngaol.agallery.core.navigation

import androidx.navigation3.runtime.NavKey
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import kotlinx.serialization.Serializable

/**
 * Nav3 route keys. Tiap layar = satu NavKey.
 *
 * @Serializable diperlukan supaya backstack bisa di-save/restore (selamat dari
 * process death) lewat rememberNavBackStack.
 */
sealed interface Screen : NavKey {
    /** Root tunggal (tab pager Settings/Gallery/Albums). */
    @Serializable
    data object Home : Screen

    /**
     * [initialIndex] = posisi item saat di-tap di grid.
     * [albumKey] = kunci album sumber (mis. "camera", "recent", "favorites",
     * atau "bucket:12345"). null = tab Gallery utama (folder kamera).
     */
    @Serializable
    data class PhotoViewer(
        val mediaId: Long,
        val initialIndex: Int,
        val sortOrder: GallerySortOrder,
        val albumKey: String? = null,
    ) : Screen

    /** Isi satu album (folder atau cerdas), ditampilkan sebagai grid ter-scope. */
    @Serializable
    data class AlbumDetail(
        val albumKey: String,
        val albumName: String,
    ) : Screen

    /**
     * Layar app-level Trash. Item-nya di-observe dari tabel Room `trashed`;
     * bukan bagian dari AlbumDetail karena punya aksi khusus (Restore &
     * Delete forever) dan bukan MediaStore scope biasa.
     */
    @Serializable
    data object Trash : Screen
}
