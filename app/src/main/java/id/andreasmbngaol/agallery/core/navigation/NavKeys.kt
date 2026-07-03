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
    /**
     * Root tunggal berisi dua tab Gallery/Albums yang bisa di-swipe horizontal.
     * Tab mana yang aktif dikelola INTERNAL oleh HorizontalPager di
     * HomeTabsScreen, jadi cukup satu key tanpa parameter. Dulu Gallery &
     * Albums adalah dua key terpisah yang saling replace.
     */
    @Serializable
    data object Home : Screen

    /**
     * [initialIndex] = posisi item saat di-tap di grid, dipakai sebagai halaman
     * awal HorizontalPager di PhotoViewer. Nilai ini bertahan lewat process
     * death karena @Serializable.
     */
    @Serializable
    data class PhotoViewer(
        val mediaId: Long,
        val initialIndex: Int,
        val sortOrder: GallerySortOrder,
    ) : Screen
}
