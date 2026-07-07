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
import id.andreasmbngaol.agallery.presentation.albums.AlbumDetailScreen
import id.andreasmbngaol.agallery.presentation.albums.CreateAlbumScreen
import id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen
import id.andreasmbngaol.agallery.presentation.tools.qr.QrGeneratorScreen
import id.andreasmbngaol.agallery.presentation.trash.TrashScreen
import id.andreasmbngaol.agallery.presentation.viewer.PhotoViewerScreen

/**
 * Host navigasi Nav3 untuk AGallery. Lihat commit sebelumnya untuk detail
 * pager tab + shared element + predictive back.
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
                                onOpenAlbum = { albumKey, name ->
                                    backStack.add(
                                        Screen.AlbumDetail(
                                            albumKey = albumKey,
                                            albumName = name,
                                        ),
                                    )
                                },
                                onOpenTrash = { backStack.add(Screen.Trash) },
                                onCreateAlbum = { backStack.add(Screen.CreateAlbum) },
                                onOpenQrGenerator = { backStack.add(Screen.QrGenerator) },
                            )
                        }

                        is Screen.PhotoViewer -> NavEntry(key) {
                            PhotoViewerScreen(
                                initialIndex = key.initialIndex,
                                sortOrder = key.sortOrder,
                                albumKey = key.albumKey,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }

                        is Screen.AlbumDetail -> NavEntry(key) {
                            AlbumDetailScreen(
                                albumKey = key.albumKey,
                                albumName = key.albumName,
                                onBack = { backStack.removeLastOrNull() },
                                onMediaClick = { id, index, sort ->
                                    backStack.add(
                                        Screen.PhotoViewer(
                                            mediaId = id,
                                            initialIndex = index,
                                            sortOrder = sort,
                                            albumKey = key.albumKey,
                                        ),
                                    )
                                },
                            )
                        }

                        is Screen.Trash -> NavEntry(key) {
                            // TrashScreen kini membaca componentStyle & edgeEffectMode
                            // sendiri dari TrashViewModel (via GetSettingsUseCase),
                            // jadi styling-nya konsisten dgn layar lain.
                            TrashScreen(
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }

                        is Screen.CreateAlbum -> NavEntry(key) {
                            CreateAlbumScreen(
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }

                        is Screen.QrGenerator -> NavEntry(key) {
                            QrGeneratorScreen(
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
