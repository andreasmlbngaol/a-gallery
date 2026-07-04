package id.andreasmbngaol.agallery.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.core.ui.GalleryTab
import id.andreasmbngaol.agallery.core.ui.GalleryTabScaffold
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.presentation.albums.AlbumsScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryGridScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryViewModel
import id.andreasmbngaol.agallery.presentation.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PageSettings = 0
private const val PageGallery = 1
private const val PageAlbums = 2
private const val HomePageCount = 3

private val NavSwipePxPerPage = 72.dp

@Composable
fun HomeTabsScreen(
    onMediaClick: (mediaId: Long, index: Int, sortOrder: GallerySortOrder) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenAlbum: (albumKey: String, name: String) -> Unit = { _, _ -> },
    onOpenTrash: () -> Unit = {},
) {
    val galleryViewModel: GalleryViewModel = koinViewModel()
    val sortOrder by galleryViewModel.sortOrder.collectAsState()
    val toggleSort: () -> Unit = { galleryViewModel.toggleSortOrder() }
    val previewItem by galleryViewModel.previewItem.collectAsState()
    // Long-press preview aktif: dari GalleryGridScreen (foto ditahan) ATAU
    // dari AlbumsScreen (album ditahan). Keduanya menutup nav bar.
    var albumPreviewActive by remember { mutableStateOf(false) }
    val previewActive = previewItem != null || albumPreviewActive

    val componentStyleChosen by galleryViewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)

    val edgeEffectMode by galleryViewModel.edgeEffectMode.collectAsState()

    var contentScrolling by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = PageGallery, pageCount = { HomePageCount })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navPxPerPage = with(density) { NavSwipePxPerPage.toPx() }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val selectedTab = when (pagerState.currentPage) {
        PageSettings -> GalleryTab.Settings
        PageAlbums -> GalleryTab.Albums
        else -> GalleryTab.Gallery
    }

    GalleryTabScaffold(
        selectedTab = selectedTab,
        onSelectTab = { tab ->
            val target = when (tab) {
                GalleryTab.Settings -> PageSettings
                GalleryTab.Albums -> PageAlbums
                else -> PageGallery
            }
            scope.launch { pagerState.animateScrollToPage(target) }
        },
        onOpenSearch = onOpenSearch,
        sortOrder = sortOrder,
        onToggleSort = toggleSort,
        barVisible = !previewActive,
        componentStyle = componentStyle,
        contentInteracting = contentScrolling,
        onNavBarDragStart = { settleJob?.cancel() },
        onNavBarDrag = { dragPx ->
            val pageSize = pagerState.layoutInfo.pageSize
            if (pageSize > 0 && navPxPerPage > 0f) {
                pagerState.dispatchRawDelta(dragPx / navPxPerPage * pageSize)
            }
        },
        onNavBarDragEnd = {
            settleJob = scope.launch {
                val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, HomePageCount - 1)
                pagerState.animateScrollToPage(target)
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !previewActive,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                PageSettings -> SettingsScreen()
                PageAlbums -> AlbumsScreen(
                    edgeEffectMode = edgeEffectMode,
                    // Diteruskan supaya tombol Pin/Unpin di hold-overlay
                    // ikut styling SOLID / FROSTED / GLASS (sama dgn pattern
                    // StyledCircleBackButton & PhotoContextMenu di Gallery).
                    componentStyle = componentStyle,
                    onAlbumClick = onOpenAlbum,
                    onOpenTrash = onOpenTrash,
                    onPreviewActiveChange = { albumPreviewActive = it },
                )
                else -> GalleryGridScreen(
                    onMediaClick = onMediaClick,
                    onScrollStateChange = { contentScrolling = it },
                    viewModel = galleryViewModel,
                )
            }
        }
    }
}
