package id.andreasmbngaol.agallery.presentation.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.mediaScopeFromKey
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import id.andreasmbngaol.agallery.presentation.gallery.GalleryGridScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Layar isi satu album -- me-REUSE [GalleryGridScreen] apa adanya (grid,
 * long-press preview, favorite/trash/delete, pull-to-refresh) hanya di-scope
 * ke [albumKey]. VM di-key per album biar tak nabrak instance galeri utama.
 *
 * Wrapper Box memakai [sharedPhotoElement] dgn key yang sama seperti cover di
 * AlbumsScreen -> transisi container-transform: album meluas dari tile-nya
 * ke full screen.
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
