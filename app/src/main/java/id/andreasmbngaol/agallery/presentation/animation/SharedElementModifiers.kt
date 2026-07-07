package id.andreasmbngaol.agallery.presentation.animation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** Shared-photo bounds-transform duration (ms). Tune here. */
private const val SharedPhotoBoundsDurationMillis = 500

/**
 * Cross-content fade duration for sharedBounds (ms). Roughly half of the bounds
 * duration for smoothness — the grid content (Crop 1:1) fades out while the
 * viewer content (Fit) fades in, inside the moving bounds.
 */
private const val SharedPhotoFadeDurationMillis = 260

/**
 * Modifier helper for a shared-element transition between NavEntries (Nav3).
 *
 * We use [SharedTransitionScope.sharedBounds][androidx.compose.animation.SharedTransitionScope]
 * (not `sharedElement`) because the two sides differ visually: the grid thumbnail
 * uses ContentScale.Crop 1:1 while the viewer uses ZoomableAsyncImage, which
 * defaults to Fit. `sharedElement` would snap to the target ContentScale at the
 * start of the transition — the cause of the "grows to max zoom before moving"
 * bug. `sharedBounds` cross-fades the two sides' content inside the moving
 * bounds, so there is no snap.
 *
 * Requires two scopes:
 * - [androidx.compose.animation.SharedTransitionScope] from [LocalSharedTransitionScope]
 *   (provided by `SharedTransitionLayout` at the navigation root).
 * - [androidx.compose.animation.AnimatedContentScope] from [LocalNavAnimatedContentScope]
 *   (provided automatically by `NavDisplay` inside each NavEntry).
 *
 * If either is unavailable (e.g. used in a preview or outside NavDisplay), this
 * modifier is a no-op. The `LocalInspectionMode` guard prevents a crash in
 * `@Preview` because [LocalNavAnimatedContentScope] has no default value.
 *
 * @param key unique key to match the element across the two screens. Make it
 * exactly the same in the grid thumbnail and the viewer page (e.g. `"photo-$id"`).
 */
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
            boundsTransform = { _, _ ->
                tween(SharedPhotoBoundsDurationMillis)
            },
        )
    }
}
