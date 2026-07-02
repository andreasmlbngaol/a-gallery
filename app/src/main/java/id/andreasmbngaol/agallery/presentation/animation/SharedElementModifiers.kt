package id.andreasmbngaol.agallery.presentation.animation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** Durasi bounds transform shared photo (ms). Tune di sini. */
private const val SharedPhotoBoundsDurationMillis = 500

/**
 * Durasi fade cross-content sharedBounds (ms). Sekitar setengah durasi
 * bounds biar mulus — konten grid (Crop 1:1) fade-out sambil konten viewer
 * (Fit) fade-in, di dalam bounds yang lagi bergerak.
 */
private const val SharedPhotoFadeDurationMillis = 260

/**
 * Modifier helper untuk shared element transition antar NavEntry (Nav3).
 *
 * Kita pakai [SharedTransitionScope.sharedBounds][androidx.compose.animation.SharedTransitionScope]
 * (bukan `sharedElement`) karena konten kedua sisi berbeda visual: thumbnail
 * grid pakai ContentScale.Crop 1:1, viewer pakai ZoomableAsyncImage yang
 * default-nya Fit. `sharedElement` akan snap ke ContentScale target di awal
 * transisi — itu penyebab bug "membesar sampai max zoom baru bergeser".
 * `sharedBounds` menyilangkan (cross-fade) konten dua sisi di dalam bounds
 * yang lagi bergerak, jadi tidak ada snap.
 *
 * Butuh dua scope:
 * - [androidx.compose.animation.SharedTransitionScope] dari [LocalSharedTransitionScope]
 *   (di-provide oleh `SharedTransitionLayout` di root navigasi).
 * - [androidx.compose.animation.AnimatedContentScope] dari [LocalNavAnimatedContentScope]
 *   (di-provide otomatis oleh `NavDisplay` di dalam tiap NavEntry).
 *
 * Kalau salah satu tidak tersedia (mis. dipakai di preview atau di luar
 * NavDisplay), modifier ini no-op. `LocalInspectionMode` guard mencegah crash
 * di `@Preview` karena [LocalNavAnimatedContentScope] tidak punya default value.
 *
 * @param key key unik untuk mencocokkan elemen di dua layar. Pastikan sama
 * persis di grid thumbnail dan viewer page (mis. `"photo-$id"`).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhotoElement(key: Any): Modifier {
    if (LocalInspectionMode.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        this@sharedPhotoElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedScope,
            enter = fadeIn(tween(SharedPhotoFadeDurationMillis)),
            exit = fadeOut(tween(SharedPhotoFadeDurationMillis)),
            boundsTransform = BoundsTransform { _, _ ->
                tween(SharedPhotoBoundsDurationMillis)
            },
        )
    }
}
