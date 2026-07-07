package id.andreasmbngaol.agallery.presentation.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.album.mediaScopeFromKey
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import id.andreasmbngaol.agallery.presentation.gallery.GalleryGridScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Single-album content screen -- REUSES [GalleryGridScreen] as-is (grid,
 * long-press preview, favorite/trash/delete, pull-to-refresh), just scoped to
 * [albumKey]. The VM is keyed per album so it does not clash with the main
 * gallery instance.
 *
 * The wrapper Box uses [sharedPhotoElement] with the same key as the cover in
 * AlbumsScreen -> container-transform transition: the album expands from its
 * tile to full screen.
 */
@Composable
fun AlbumDetailScreen(
    albumKey: String,
    albumName: String,
    onBack: () -> Unit,
    onMediaClick: (mediaId: Long, index: Int, sortOrder: GallerySortOrder) -> Unit,
) {
    val scope = remember(albumKey) { mediaScopeFromKey(albumKey) }
    val viewModel: GalleryViewModel = koinViewModel(key = "album_$albumKey")
    LaunchedEffect(albumKey) { viewModel.setScope(scope) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .sharedPhotoElement(key = "albumcover-$albumKey"),
    ) {
        GalleryGridScreen(
            onMediaClick = onMediaClick,
            staticTitle = albumName,
            onBack = onBack,
            selectionEnabled = true,
            albumKey = albumKey,
            viewModel = viewModel,
        )
    }
}
