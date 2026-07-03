package id.andreasmbngaol.agallery.core.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import id.andreasmbngaol.agallery.presentation.animation.LocalSharedTransitionScope
import id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen
import id.andreasmbngaol.agallery.presentation.viewer.PhotoViewerScreen

/**
 * Host navigasi Nav3 untuk AGallery.
 *
 * ## Root tunggal + tab swipeable
 *
 * [Screen.Home] adalah satu-satunya root, berisi HorizontalPager dua tab
 * (Gallery / Albums) di [HomeTabsScreen]. Dulu Gallery & Albums adalah dua
 * NavEntry terpisah yang saling replace; sekarang keduanya jadi halaman pager
 * dalam SATU entry — supaya bisa di-swipe kiri/kanan sementara floating bar
 * tetap diam. Sort state ikut pindah ke [HomeTabsScreen].
 *
 * ## Transisi & predictive back
 *
 * [NavDisplay] diberi transition spec eksplisit:
 * - `transitionSpec` (push): cross-fade halus.
 * - `popTransitionSpec` & `predictivePopTransitionSpec` (pop / predictive
 *   back, mis. kembali dari PhotoViewer): layar yang KELUAR mengecil sedikit
 *   (`scaleOut`) + fade, sementara layar sebelumnya fade-in. Dikombinasikan
 *   dengan shared element (foto mengecil balik ke thumbnail), gesture
 *   predictive-back terasa mengikuti jari & mulus — bukan lagi potong kaku.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AGalleryNavDisplay() {
    val backStack = rememberNavBackStack(Screen.Home)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = {
                    fadeIn(tween(280)) togetherWith fadeOut(tween(280))
                },
                popTransitionSpec = {
                    fadeIn(tween(280)) togetherWith
                        (fadeOut(tween(280)) + scaleOut(targetScale = 0.92f))
                },
                predictivePopTransitionSpec = {
                    fadeIn(tween(280)) togetherWith
                        (fadeOut(tween(280)) + scaleOut(targetScale = 0.92f))
                },
                entryProvider = { key ->
                    when (key) {
                        is Screen.Home -> NavEntry(key) {
                            HomeTabsScreen(
                                onMediaClick = { id, index, sort ->
                                    backStack.add(
                                        Screen.PhotoViewer(
                                            mediaId = id,
                                            initialIndex = index,
                                            sortOrder = sort,
                                        ),
                                    )
                                },
                                onOpenSearch = {
                                    // TODO: tambahkan Screen.Search + rute-nya.
                                },
                            )
                        }

                        is Screen.PhotoViewer -> NavEntry(key) {
                            PhotoViewerScreen(
                                mediaId = key.mediaId,
                                initialIndex = key.initialIndex,
                                sortOrder = key.sortOrder,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }

                        else -> NavEntry(key) { }
                    }
                },
            )
        }
    }
}
