package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.andreasmbngaol.agallery.domain.model.ComponentStyle

/** Liquid glass (RuntimeShader) butuh Android 13 (API 33)+. */
const val GLASS_MIN_SDK = 33

/**
 * Resolusi gaya komponen efektif:
 * - chosen null -> FROSTED (default cerdas: mulus & tetap berkesan kaca).
 * - GLASS di perangkat < API 33 -> turun ke FROSTED (RuntimeShader absen).
 * - selain itu -> pakai pilihan user apa adanya.
 */
fun resolveComponentStyle(chosen: ComponentStyle?, sdkInt: Int): ComponentStyle {
    val base = chosen ?: ComponentStyle.FROSTED
    return if (base == ComponentStyle.GLASS && sdkInt < GLASS_MIN_SDK) {
        ComponentStyle.FROSTED
    } else {
        base
    }
}

@Composable
fun rememberEffectiveComponentStyle(chosen: ComponentStyle?): ComponentStyle =
    remember(chosen) { resolveComponentStyle(chosen, Build.VERSION.SDK_INT) }

/** True kalau perangkat sanggup liquid glass (RuntimeShader). */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= GLASS_MIN_SDK

/**
 * Apakah gaya ini menggambar backdrop kaca Kyant (drawBackdrop = render konten di
 * belakang ke layer + efek)? Butuh RuntimeShader = API 33+.
 * - GLASS   = ya. Efek PENUH: vibrancy + blur + lens (refraction "liquid glass").
 * - FROSTED = ya, TAPI hanya vibrancy + veil "haze" (lihat [usesBlur] & [usesLens]:
 *   keduanya false). Jadi bentuknya TETAP seperti kaca (konten di belakang tembus),
 *   TANPA blur & TANPA distorsi -> kesan kabut/haze, bukan liquid glass.
 * - SOLID   = TIDAK -> fill warna opaque biasa.
 * Catatan: [resolveComponentStyle] sudah memetakan GLASS -> FROSTED di < API 33.
 * Di perangkat < 33 (RuntimeShader absen) ini false untuk semua gaya -> fallback fill.
 */
fun ComponentStyle.drawsBackdrop(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    this != ComponentStyle.SOLID && sdkInt >= GLASS_MIN_SDK

/**
 * Apakah efek lens/refraction (pembiasan "liquid glass") dipakai? Hanya GLASS.
 * FROSTED tidak -> tak ada distorsi (sesuai kesan "haze", bukan kaca cair).
 */
fun ComponentStyle.usesLens(): Boolean = this == ComponentStyle.GLASS

/**
 * Apakah efek blur (gaussian) dipakai di backdrop? Hanya GLASS.
 * FROSTED sengaja TANPA blur -> pakai veil/haze (tint) saja. Selain sesuai
 * permintaan "jangan blur, pakai haze", ini juga MENGHILANGKAN artefak heksagon
 * dari kernel blur Kyant di elemen bulat kecil (mis. tombol Sort/Search) yang
 * dulu tampak karena di tombol tak ada lens yang menutupinya.
 */
fun ComponentStyle.usesBlur(): Boolean = this == ComponentStyle.GLASS

/**
 * Apakah backdrop di-capture LIVE tiap frame?
 * - GLASS = ya (refraction ngikut gerakan, mis. TETAP kaca saat swipe).
 * - FROSTED = tidak; snapshot dibekukan saat ada interaksi (scroll/swipe) supaya
 *   hemat GPU. Pemanggil yang punya sinyal interaksi mematikan capture saat itu.
 */
fun ComponentStyle.usesLiveBackdrop(): Boolean = this == ComponentStyle.GLASS
