package id.andreasmbngaol.agallery.core.navigation

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
import id.andreasmbngaol.agallery.presentation.ai.AiModelsScreen
import id.andreasmbngaol.agallery.presentation.ai.BackgroundRemoverScreen
import id.andreasmbngaol.agallery.presentation.albums.AlbumDetailScreen
import id.andreasmbngaol.agallery.presentation.albums.CreateAlbumScreen
import id.andreasmbngaol.agallery.presentation.animation.LocalSharedTransitionScope
import id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen
import id.andreasmbngaol.agallery.presentation.tools.qr.QrGeneratorScreen
import id.andreasmbngaol.agallery.presentation.trash.TrashScreen
import id.andreasmbngaol.agallery.presentation.viewer.PhotoViewerScreen

/**
 * Nav3 navigation host for AGallery.
 *
 * Wraps the back stack in a [SharedTransitionLayout] so destinations can run
 * shared-element transitions, and defines the fade/scale specs for forward,
 * pop, and predictive-back navigation. The [androidx.navigation3.runtime.entryProvider] maps each
 * [Screen] key to its destination composable and threads the navigation
 * callbacks that push or pop entries on the back stack.
 */
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
                                onOpenAiModels = { backStack.add(Screen.AiModels) },
                            )
                        }

                        is Screen.PhotoViewer -> NavEntry(key) {
                            PhotoViewerScreen(
                                initialIndex = key.initialIndex,
                                sortOrder = key.sortOrder,
                                albumKey = key.albumKey,
                                onBack = { backStack.removeLastOrNull() },
                                onOpenBackgroundRemover = { mediaUri, displayName ->
                                    backStack.add(
                                        Screen.BackgroundRemover(
                                            mediaUri = mediaUri,
                                            displayName = displayName,
                                        ),
                                    )
                                },
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

                        is Screen.AiModels -> NavEntry(key) {
                            AiModelsScreen(
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }

                        is Screen.BackgroundRemover -> NavEntry(key) {
                            BackgroundRemoverScreen(
                                mediaUri = key.mediaUri,
                                displayName = key.displayName,
                                onBack = { backStack.removeLastOrNull() },
                                onOpenAiModels = { backStack.add(Screen.AiModels) },
                            )
                        }

                        else -> NavEntry(key) { }
                    }
                },
            )
        }
    }
}
