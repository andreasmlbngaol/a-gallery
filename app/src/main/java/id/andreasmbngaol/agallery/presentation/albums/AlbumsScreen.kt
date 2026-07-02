package id.andreasmbngaol.agallery.presentation.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Konten tab Albums — daftar/grid album MediaStore.
 *
 * Untuk sekarang masih placeholder. Floating bar (Gallery/Albums, Settings,
 * Sort) digambar oleh host pager
 * [id.andreasmbngaol.agallery.presentation.home.HomeTabsScreen], BUKAN di
 * sini — dulu screen ini membungkus dirinya sendiri dengan scaffold.
 *
 * TODO: implementasi `GetAlbumsUseCase` + grid album.
 */
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Albums (not implemented yet)",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
