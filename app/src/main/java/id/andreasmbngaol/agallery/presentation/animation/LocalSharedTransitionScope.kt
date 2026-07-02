package id.andreasmbngaol.agallery.presentation.animation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal untuk mem-propagate [SharedTransitionScope] dari
 * `SharedTransitionLayout` di [id.andreasmbngaol.agallery.core.navigation.AGalleryNavDisplay]
 * ke seluruh layar tanpa perlu di-drill lewat parameter tiap composable.
 *
 * Default `null`: [Modifier.sharedPhotoElement][id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement]
 * akan no-op bila dipakai di luar `SharedTransitionLayout` (mis. di dalam
 * `@Preview` atau sebelum navigasi ter-inisialisasi).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
