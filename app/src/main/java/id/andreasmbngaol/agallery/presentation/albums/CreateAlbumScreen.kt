package id.andreasmbngaol.agallery.presentation.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Check
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import id.andreasmbngaol.agallery.presentation.viewer.NewAlbumNameDialog
import org.koin.androidx.compose.koinViewModel

/**
 * Alur buat album baru:
 * 1) Minta nama album (dialog, textfield squircle).
 * 2) Pilih album sumber (grid 3 kolom) -> buka -> pilih foto (grid 3 kolom).
 * 3) Tekan Create (FAB kanan-bawah) -> copy semua ke DCIM/<nama>/.
 * Batal = album baru tidak dibuat (konsep copy, sumber tetap utuh).
 *
 * Top bar, edge effect, dan gaya komponen mengikuti setting global
 * (Solid / Frosted / Glass) seperti layar lain.
 */
@Composable
fun CreateAlbumScreen(
    onBack: () -> Unit,
    viewModel: CreateAlbumViewModel = koinViewModel(),
) {
    val albums by viewModel.albums.collectAsState()
    val albumMedia by viewModel.albumMedia.collectAsState()
    val creating by viewModel.creating.collectAsState()

    val componentStyleChosen by viewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)
    val edgeChosen by viewModel.edgeEffectMode.collectAsState()
    val effectiveMode = rememberEffectiveEdgeEffectMode(edgeChosen)
    val backdrop = rememberLayerBackdrop()

    var albumName by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<Album?>(null) }
    val selected = remember { mutableStateMapOf<Long, MediaItem>() }

    LaunchedEffect(Unit) { viewModel.loadAlbums() }
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    val name = albumName
    if (name == null) {
        // Gate nama dulu; batal di sini = keluar tanpa membuat apa pun.
        NewAlbumNameDialog(
            title = "New album",
            confirmLabel = "Next",
            onConfirm = { albumName = it },
            onDismiss = onBack,
        )
        return
    }

    fun goBack() {
        if (openedAlbum != null) {
            openedAlbum = null
            viewModel.closeAlbum()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) { goBack() }

    SystemBarScrim(
        mode = effectiveMode,
        topOverlay = {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = { goBack() },
                    contentDescription = "Back",
                    style = componentStyle,
                    backdrop = backdrop,
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (openedAlbum == null) "Pilih album sumber foto"
                        else "${selected.size} dipilih",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // FAB Create: kanan-bawah (bukan lagi di top bar).
            if (selected.isNotEmpty()) {
                CreateFab(
                    count = selected.size,
                    enabled = !creating,
                    style = componentStyle,
                    backdrop = backdrop,
                    onClick = { viewModel.create(name, selected.values.toList()) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 20.dp),
                )
            }
        },
    ) { sourceModifier ->
        val gridModifier = if (componentStyle.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = gridModifier.then(sourceModifier)) {
            val topPad = 104.dp
            if (openedAlbum == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = topPad, bottom = 120.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums, key = { it.key }) { album ->
                        SourceAlbumTile(
                            album = album,
                            onClick = {
                                openedAlbum = album
                                viewModel.openAlbum(album.scope)
                            },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 2.dp, end = 2.dp, top = topPad, bottom = 120.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(albumMedia, key = { it.id }) { item ->
                        val isSel = selected.containsKey(item.id)
                        PickPhotoCell(
                            item = item,
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.remove(item.id)
                                else selected[item.id] = item
                            },
                        )
                    }
                }
            }
        }
    }
}

/** FAB Create kanan-bawah dengan gaya liquid glass (kapsul). */
@Composable
private fun CreateFab(
    count: Int,
    enabled: Boolean,
    style: ComponentStyle,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) 0.4f else 0.3f,
    )
    val fallbackTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (style == ComponentStyle.FROSTED) 0.55f else 0.95f,
    )
    val glass = if (style.drawsBackdrop()) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                if (style.usesBlur()) blur(4.dp.toPx())
                if (style.usesLens()) lens(12.dp.toPx(), 16.dp.toPx())
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        Modifier.clip(RoundedCornerShape(percent = 50)).background(fallbackTint)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .then(glass)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = PhosphorIcons.Bold.Check,
            contentDescription = "Create",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Create ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun SourceAlbumTile(
    album: Album,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            text = "${album.itemCount} item",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun PickPhotoCell(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        SelectionCheck(
            selected = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

/** Lingkaran centang (ikon Phosphor Check saat terpilih). */
@Composable
private fun SelectionCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.35f),
            )
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = PhosphorIcons.Bold.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
