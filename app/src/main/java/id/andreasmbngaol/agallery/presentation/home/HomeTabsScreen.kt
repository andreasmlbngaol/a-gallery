package id.andreasmbngaol.agallery.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import id.andreasmbngaol.agallery.core.ui.GalleryTab
import id.andreasmbngaol.agallery.core.ui.GalleryTabScaffold
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.presentation.albums.AlbumsScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryGridScreen
import id.andreasmbngaol.agallery.presentation.gallery.GalleryViewModel
import id.andreasmbngaol.agallery.presentation.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch

// Urutan halaman pager: Settings (0) | Gallery (1) | Albums (2). Gallery jadi
// halaman awal (tengah) supaya swipe kanan -> Settings, swipe kiri -> Albums.
private const val PageSettings = 0
private const val PageGallery = 1
private const val PageAlbums = 2
private const val HomePageCount = 3

/**
 * Root berisi dua tab (Gallery / Albums) yang bisa DI-SWIPE horizontal.
 *
 * ## Kenapa pager di-hoist di sini (bukan di tiap screen)
 *
 * Floating bar ([GalleryTabScaffold]) digambar SEKALI di sini, DI LUAR
 * [HorizontalPager], supaya bar tetap DIAM saat halaman digeser — cuma konten
 * (grid galeri ↔ daftar album) yang ikut jari. Dulu tiap screen membungkus
 * dirinya sendiri dengan scaffold + tombolnya sendiri; sekarang scaffold jadi
 * milik host ini.
 *
 * ## Sinkronisasi tab ↔ pager
 * - Swipe halaman → [PagerState.currentPage] berubah → tab ter-highlight ikut.
 * - Tap tab di bar → `animateScrollToPage` menggeser pager ke halaman itu.
 *
 * Jadi: swipe dari Gallery ke kiri → Albums, dan dari Albums ke kanan →
 * Gallery, plus tetap bisa tap tab seperti sebelumnya.
 *
 * ## Sort state (satu sumber kebenaran, dipersist)
 * `sortOrder` dibaca dari [GalleryViewModel] yang mem-persist pilihan ke
 * DataStore, jadi TETAP walau aplikasi ditutup (bukan cuma rememberSaveable
 * yang hilang saat proses di-kill). Instance VM yang sama dipakai bar & grid.
 */
@Composable
fun HomeTabsScreen(
    onMediaClick: (mediaId: Long, index: Int, sortOrder: GallerySortOrder) -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    // Satu instance GalleryViewModel dipakai bareng oleh bar (di sini) & grid
    // (di bawah), supaya sort order = SATU sumber kebenaran yang dipersist di
    // DataStore (tetap walau app ditutup).
    val galleryViewModel: GalleryViewModel = koinViewModel()
    val sortOrder by galleryViewModel.sortOrder.collectAsState()
    val toggleSort: () -> Unit = { galleryViewModel.toggleSortOrder() }
    // Preview long-press aktif? Kalau ya: kunci swipe antar-tab & sembunyikan
    // (tutup) floating nav bar supaya tak bisa pindah tab saat menahan foto.
    val previewItem by galleryViewModel.previewItem.collectAsState()
    val previewActive = previewItem != null

    // "Mode hemat": kalau user set Edge Effect = OFF di Settings, matikan juga
    // liquid glass di floating nav bar (blur real-time = paling berat di GPU).
    val edgeEffectMode by galleryViewModel.edgeEffectMode.collectAsState()
    val glassEnabled =
        rememberEffectiveEdgeEffectMode(edgeEffectMode) != EdgeEffectMode.OFF

    val pagerState = rememberPagerState(initialPage = PageGallery, pageCount = { HomePageCount })
    val scope = rememberCoroutineScope()

    // Tab ter-highlight ngikutin halaman pager yang lagi aktif.
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
        glassEnabled = glassEnabled,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Kunci swipe antar-tab saat preview long-press aktif.
            userScrollEnabled = !previewActive,
            // Kedua halaman cuma dua, jadi biarkan tetangganya tetap ke-compose
            // supaya state grid & posisi scroll tak reset saat swipe bolak-balik.
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                PageSettings -> SettingsScreen()
                PageAlbums -> AlbumsScreen()
                else -> GalleryGridScreen(
                    onMediaClick = onMediaClick,
                    viewModel = galleryViewModel,
                )
            }
        }
    }
}
