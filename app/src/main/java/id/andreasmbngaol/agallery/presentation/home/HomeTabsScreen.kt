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

// Urutan halaman pager: Settings (0) | Gallery (1) | Albums (2). Gallery jadi
// halaman awal (tengah) supaya swipe kanan -> Settings, swipe kiri -> Albums.
private const val PageSettings = 0
private const val PageGallery = 1
private const val PageAlbums = 2
private const val HomePageCount = 3

// Jarak GESER pada pill bar yang setara SATU halaman penuh pager. Dipakai untuk
// memetakan drag px -> fraksi halaman saat MENGGERAKKAN pager secara kontinu
// lewat bar (0 -> 0,4 dst). Makin kecil = makin sensitif.
private val NavSwipePxPerPage = 72.dp

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

    // Gaya komponen (Solid/Frosted/Glass) dari Settings -> dipakai floating nav
    // bar. DIPISAH dari edge effect: glass real-time yang berat hanya kalau user
    // benar-benar memilih Glass. Sudah di-resolve (GLASS -> FROSTED di < API 33).
    val componentStyleChosen by galleryViewModel.componentStyle.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(componentStyleChosen)

    // Efek tepi (Off/Darken/Blurry) dari Settings -> dipakai topbar tab Albums
    // supaya konsisten dgn Gallery. (Tab Settings pakai VM-nya sendiri.)
    val edgeEffectMode by galleryViewModel.edgeEffectMode.collectAsState()

    // Status scroll grid galeri -> untuk MEMBEKUKAN capture backdrop nav bar saat
    // gaya FROSTED (hemat GPU). GLASS tetap live; SOLID tak pernah capture.
    var contentScrolling by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = PageGallery, pageCount = { HomePageCount })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Skala: berapa px scroll pager per 1 px geser di bar (1 halaman = pageSize).
    val navPxPerPage = with(density) { NavSwipePxPerPage.toPx() }
    // Job animasi SNAP terakhir; dibatalkan saat gesture bar baru dimulai supaya
    // dispatchRawDelta tak berebut dengan animasi settle sebelumnya.
    var settleJob by remember { mutableStateOf<Job?>(null) }

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
        componentStyle = componentStyle,
        contentInteracting = contentScrolling,
        // GESER bar = gerakkan pager KONTINU (mengikuti jari), lalu SNAP ke tab
        // terdekat saat dilepas. Highlight tab tetap ikut currentPage (baru
        // pindah setelah melewati titik tengah antar-halaman).
        onNavBarDragStart = { settleJob?.cancel() },
        onNavBarDrag = { dragPx ->
            val pageSize = pagerState.layoutInfo.pageSize
            if (pageSize > 0 && navPxPerPage > 0f) {
                // Geser jari KIRI (dragPx negatif) -> pager MAJU ke tab kanan
                // (delta positif), jadi tandanya dibalik.
                pagerState.dispatchRawDelta(-dragPx / navPxPerPage * pageSize)
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
            // Kunci swipe antar-tab saat preview long-press aktif.
            userScrollEnabled = !previewActive,
            // Swipe KONTEN sengaja dibatasi 1 halaman per geseran (perilaku pager
            // default) biar tak kelebihan. Untuk lompat jauh (mis. Settings->Albums)
            // GESER pada bar menu bawah (lihat GalleryTabScaffold).
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                PageSettings -> SettingsScreen()
                PageAlbums -> AlbumsScreen(edgeEffectMode = edgeEffectMode)
                else -> GalleryGridScreen(
                    onMediaClick = onMediaClick,
                    onScrollStateChange = { contentScrolling = it },
                    viewModel = galleryViewModel,
                )
            }
        }
    }
}
