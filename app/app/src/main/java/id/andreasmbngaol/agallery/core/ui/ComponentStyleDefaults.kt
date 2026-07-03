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
 * belakang ke layer + efek blur/lens)?
 * - GLASS & FROSTED = ya, TAPI butuh RuntimeShader (API 33+).
 * - SOLID = TIDAK -> pakai fill warna biasa, konten di belakang TAK di-render
 *   ulang ke layer (nol overhead backdrop, tak render background).
 * Catatan: [resolveComponentStyle] sudah memetakan GLASS -> FROSTED di < API 33,
 * jadi di perangkat < 33 ini otomatis false untuk SEMUA gaya (fallback fill).
 */
fun ComponentStyle.drawsBackdrop(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    this != ComponentStyle.SOLID && sdkInt >= GLASS_MIN_SDK

/**
 * Apakah efek lens/refraction (pembiasan "liquid glass") dipakai?
 * Hanya GLASS. FROSTED sengaja blur SAJA (kaca buram) -> lebih ringan & tak ada
 * artefak refraction di elemen kecil (mis. tombol bulat yang tadinya "hexagon").
 */
fun ComponentStyle.usesLens(): Boolean = this == ComponentStyle.GLASS

/**
 * Apakah backdrop di-capture LIVE tiap frame?
 * - GLASS = ya (refraction ngikut gerakan, mis. TETAP kaca saat swipe).
 * - FROSTED = tidak; snapshot dibekukan saat ada interaksi (scroll/swipe) supaya
 *   hemat GPU. Pemanggil yang punya sinyal interaksi mematikan capture saat itu.
 */
fun ComponentStyle.usesLiveBackdrop(): Boolean = this == ComponentStyle.GLASS
