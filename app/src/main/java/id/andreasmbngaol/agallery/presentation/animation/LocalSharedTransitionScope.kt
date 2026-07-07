package id.andreasmbngaol.agallery.presentation.animation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for propagating [SharedTransitionScope] from the
 * `SharedTransitionLayout` in [id.andreasmbngaol.agallery.core.navigation.AGalleryNavDisplay]
 * to every screen without drilling it through each composable's parameters.
 *
 * Default `null`: [Modifier.sharedPhotoElement][id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement]
 * is a no-op when used outside a `SharedTransitionLayout` (e.g. inside a
 * `@Preview` or before navigation is initialized).
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
