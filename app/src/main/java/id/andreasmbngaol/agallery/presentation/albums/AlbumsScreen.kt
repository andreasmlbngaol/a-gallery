package id.andreasmbngaol.agallery.presentation.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ImageSquare
import id.andreasmbngaol.agallery.core.permission.MediaPermissionGate
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.domain.model.Album
import org.koin.androidx.compose.koinViewModel

private val AlbumsEdgePadding = 12.dp
private val AlbumGridSpacing = 12.dp
private val AlbumCoverCorner = 16.dp

/**
 * Tab Albums: grid folder media di device (Camera, Screenshots, Download, dst),
 * masing-masing dengan sampul (item terbaru) + jumlah item.
 *
 * Konten hanya tampil kalau izin media sudah diberikan; [MediaPermissionGate]
 * yang mengurus prompt izin. Query diulang saat konten pertama tampil supaya
 * data ikut ke-load kalau izin baru saja diberikan.
 */
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    // Bottom = safe area (nav bar) + tinggi floating tab bar, biar item terakhir
    // tidak ketutup pill bar.
    val contentPadding = PaddingValues(
        top = safeDrawing.calculateTopPadding() + AlbumsEdgePadding,
        bottom = safeDrawing.calculateBottomPadding() + FloatingTabBarHeight,
        start = safeDrawing.calculateStartPadding(layoutDirection) + AlbumsEdgePadding,
        end = safeDrawing.calculateEndPadding(layoutDirection) + AlbumsEdgePadding,
    )

    MediaPermissionGate(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(Unit) { viewModel.refresh() }
        val state by viewModel.state.collectAsState()
        when (val current = state) {
            AlbumsUiState.Loading -> LoadingState()
            AlbumsUiState.Empty -> EmptyAlbumsState()
            is AlbumsUiState.Content -> AlbumsGrid(
                albums = current.albums,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AlbumGridSpacing),
        verticalArrangement = Arrangement.spacedBy(AlbumGridSpacing),
    ) {
        items(count = albums.size, key = { albums[it].id }) { index ->
            AlbumCard(album = albums[index])
        }
    }
}

@Composable
private fun AlbumCard(album: Album) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(AlbumCoverCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val cover = album.coverUri
            if (cover != null) {
                AsyncImage(
                    model = remember(cover) {
                        ImageRequest.Builder(context)
                            .data(cover)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = PhosphorIcons.Regular.ImageSquare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (album.itemCount == 1) "1 item" else "${album.itemCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyAlbumsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No albums found on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
