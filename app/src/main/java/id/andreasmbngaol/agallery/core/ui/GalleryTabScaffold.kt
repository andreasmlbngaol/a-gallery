package id.andreasmbngaol.agallery.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import id.andreasmbngaol.agallery.R
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.SortAscending
import com.adamglin.phosphoricons.bold.SortDescending
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder

/**
 * Dua tab utama root screen di AGallery.
 */
enum class GalleryTab { Settings, Gallery, Albums }

/**
 * Tinggi total footprint bar mengambang dari BAWAH area content, di luar
 * `WindowInsets.navigationBars`. Dipakai screen di dalam scaffold untuk
 * menambah `contentPadding.bottom` grid supaya item terakhir tidak ketutup.
 *
 * Perhitungan: 12dp (padding atas bar) + 52dp (tinggi tombol) + 12dp
 * (padding bawah bar) = 76dp.
 */
val FloatingTabBarHeight = 76.dp

private val FloatingButtonSize = 52.dp

// Lebar TETAP pill tab. Sengaja lebih sempit dari total 3 tab supaya tab
// tetangga "ke-peek" (terpotong) simetris di kiri/kanan tab terpilih.
private val PillWidth = 176.dp

// Gap antar chip di dalam pill.
private val TabGap = 2.dp

// Inset chip terpilih dari tepi tiap segmen (semua sisi).
private val SelectionInset = 4.dp

// Outline/halo tipis supaya text & icon tetap terbaca di atas liquid glass
// walau warna konten di belakang mirip. Blur teks dalam px, icon dalam dp.
private const val TextHaloBlur = 4f
private val IconHaloBlur = 3.dp

// ---- Tuning liquid glass (Kyant backdrop, hanya API 33+) ----
// Radius blur kaca.
private val GlassBlurRadius = 4.dp
// lens(height, amount): tinggi & besar pembiasan tepi "liquid glass".
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
// Tint tipis di atas kaca GLASS (track pill & tombol) supaya tetap tembus pandang.
private const val GlassTintAlpha = 0.22f
// Tint segmen TERPILIH (GLASS): lebih pekat -> kaca lebih "padat" sbg penanda.
private const val SelectedGlassTintAlpha = 0.55f
// Veil "haze" FROSTED di atas backdrop (drawBackdrop TANPA blur & lens). Sedikit
// lebih pekat dari GlassTintAlpha karena tak ada blur -> veil pembawa kesan kabut.
private const val FrostedHazeAlpha = 0.33f
private const val SelectedFrostedHazeAlpha = 0.6f
// Non-glass fill:
// - FROSTED: tint translusen (kaca buram statis; konten di belakang tembus).
// - SOLID  : pakai KONTRAS TONAL (surfaceContainerLow utk track, surfaceContainerHighest
//   utk terpilih), BUKAN alpha. Alpha pada warna sama di atas track opaque sewarna
//   nyaris tak kelihatan -> itu sebabnya dulu "selected" SOLID hampir tak nampak.
private const val FrostedFallbackAlpha = 0.55f
// Segmen terpilih gaya FROSTED (lebih pekat dari track frosted).
private const val SelectedFrostedAlpha = 0.8f

/**
 * Scaffold screen root: konten full-screen + bar mengambang di bawah berisi
 * pill tab [ Settings | Gallery | Albums ]. Tombol action (Sort kiri, Search
 * kanan) hanya muncul di tab Gallery.
 *
 * ## Liquid glass
 *
 * Pill tab + tombol lingkaran Settings & Sort memakai efek **liquid glass**
 * (refraction) dari library Kyant `backdrop`. Efek ini butuh `RuntimeShader`
 * = Android 13 (API 33)+. Di bawah itu otomatis fallback ke container frosted
 * solid.
 *
 * Supaya kaca bisa "membiaskan" konten di belakangnya, [content] di-tag
 * sebagai `layerBackdrop` (sumber backdrop) saat perangkat mendukung.
 */
@Composable
fun GalleryTabScaffold(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    componentStyle: ComponentStyle = ComponentStyle.FROSTED,
    // True saat konten (grid) lagi di-scroll -> nav bar FROSTED membekukan capture
    // backdrop biar hemat GPU. Tak berpengaruh untuk GLASS (selalu live) & SOLID.
    contentInteracting: Boolean = false,
    // GESER pada pill bar -> gerakkan konten pager KONTINU. Host memetakan delta
    // px ke fraksi halaman (onNavBarDrag) & meng-SNAP ke tab terdekat saat
    // gesture berakhir (onNavBarDragEnd); onNavBarDragStart membatalkan settle.
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
    onCreateAlbum: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Kapan konten (grid) di-CAPTURE sebagai sumber backdrop:
    // - GLASS  : selalu live (refraction ngikut gerakan).
    // - FROSTED / SOLID / < API 33: TIDAK pernah capture (drawsBackdrop=false) ->
    //   FROSTED pakai fill translusen statis, SOLID pakai fill opaque; grid tak
    //   di-render ulang ke layer. `contentInteracting` kini tak berpengaruh lagi
    //   (FROSTED tak lagi capture), dibiarkan demi kompatibilitas pemanggil.
    val captureBackdrop =
        componentStyle.drawsBackdrop() &&
            (componentStyle.usesLiveBackdrop() || !contentInteracting)
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // Konten (grid) jadi SUMBER backdrop yang dibiaskan bar kaca.
        val contentModifier =
            if (captureBackdrop) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        Box(modifier = contentModifier) {
            content()
        }

        // Sembunyikan (slide + fade turun) saat preview long-press aktif,
        // supaya nav bar ketutup & tak bisa dipakai pindah tab.
        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingTabBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                sortOrder = sortOrder,
                onToggleSort = onToggleSort,
                backdrop = backdrop,
                componentStyle = componentStyle,
                onNavBarDrag = onNavBarDrag,
                onNavBarDragStart = onNavBarDragStart,
                onNavBarDragEnd = onNavBarDragEnd,
                onCreateAlbum = onCreateAlbum,
            )
        }
    }
}

@Composable
private fun FloatingTabBar(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    modifier: Modifier = Modifier,
    onCreateAlbum: () -> Unit = {},
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
) {
    // Veil di atas backdrop: FROSTED pakai haze (lebih pekat, TANPA blur), GLASS tipis.
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (componentStyle == ComponentStyle.FROSTED) FrostedHazeAlpha else GlassTintAlpha,
    )
    // SpaceEvenly: jarak antar "island" (tombol<->pill) SAMA dengan margin
    // kiri/kanan bar. Pill lebar TETAP & selalu ter-center (Sort/Search
    // simetris), jadi saat tombol hilang di tab lain pill tak geser/berubah.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kiri: spacer penyeimbang supaya pill tetap ter-center terhadap
        // tombol di kanan. Gallery (Sort) & Albums (+) sama-sama butuh ini.
        // Tombol Search di Gallery untuk sementara dihapus.
        if (selectedTab == GalleryTab.Gallery || selectedTab == GalleryTab.Albums) {
            Spacer(Modifier.size(FloatingButtonSize))
        }

        // Tengah: pill tab (Settings | Gallery | Albums), lebar tetap & selalu
        // di tengah bar. Tab terpilih ditengahkan -> peek kiri/kanan simetris.
        TabSwitcher(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            backdrop = backdrop,
            componentStyle = componentStyle,
            glassTint = glassTint,
            onNavBarDrag = onNavBarDrag,
            onNavBarDragStart = onNavBarDragStart,
            onNavBarDragEnd = onNavBarDragEnd,
        )

        // Kanan: tombol Sort (khusus tab Gallery). Dipindah dari kiri ke kanan;
        // tombol Search sementara dihapus (plumbing onOpenSearch tetap ada).
        if (selectedTab == GalleryTab.Gallery) {
            CircularFloatingButton(
                onClick = onToggleSort,
                contentDescription = stringResource(R.string.action_change_sort_order),
                backdrop = backdrop,
                componentStyle = componentStyle,
                glassTint = glassTint,
            ) {
                // AnimatedContent supaya icon fade+slide saat ganti order. Arah
                // slide dibalik antar transisi supaya kesan panah "membalik".
                AnimatedContent(
                    targetState = sortOrder,
                    transitionSpec = {
                        val goingToAsc = targetState == GallerySortOrder.DateAsc
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(220)) +
                                slideInVertically(tween(260)) { h ->
                                    if (goingToAsc) -h / 2 else h / 2
                                },
                            initialContentExit = fadeOut(tween(160)) +
                                slideOutVertically(tween(200)) { h ->
                                    if (goingToAsc) h / 2 else -h / 2
                                },
                            sizeTransform = SizeTransform(clip = false),
                        )
                    },
                    label = "sort-icon",
                ) { order ->
                    HaloIcon(
                        imageVector = when (order) {
                            GallerySortOrder.DateDesc -> PhosphorIcons.Bold.SortDescending
                            GallerySortOrder.DateAsc -> PhosphorIcons.Bold.SortAscending
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Kanan (tab Albums): tombol "+" untuk buat album baru. Style-nya
        // identik dgn tombol Sort/Search (CircularFloatingButton + HaloIcon).
        if (selectedTab == GalleryTab.Albums) {
            CircularFloatingButton(
                onClick = onCreateAlbum,
                contentDescription = stringResource(R.string.new_album),
                backdrop = backdrop,
                componentStyle = componentStyle,
                glassTint = glassTint,
            ) {
                HaloIcon(
                    imageVector = PhosphorIcons.Bold.Plus,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Switcher tab: pill segmented custom (Settings | Gallery | Albums) dengan
 * background liquid glass. Segmen terpilih = "chip" kaca lebih pekat di atas
 * track kaca (bukan warna primary).
 *
 * Segmen wrap-content dengan gap kecil (2dp). Pill lebar TETAP; tab terpilih
 * di-scroll ke tengah -> tab tengah (Gallery) memberi peek simetris kiri/kanan
 * ("Settings" kepotong depan & "Albums" kepotong belakang sama besar).
 */
@Composable
private fun TabSwitcher(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    glassTint: Color,
    modifier: Modifier = Modifier,
    onNavBarDrag: (dragPx: Float) -> Unit = {},
    onNavBarDragStart: () -> Unit = {},
    onNavBarDragEnd: () -> Unit = {},
) {
    val entries = listOf(
        GalleryTab.Settings to stringResource(R.string.tab_settings),
        GalleryTab.Gallery to stringResource(R.string.tab_gallery),
        GalleryTab.Albums to stringResource(R.string.tab_albums),
    )
    val selectedIndex = entries.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Lebar px tiap tab (wrap content, jadi beda-beda) buat menghitung offset
    // scroll yang MENENGAHKAN tab terpilih di dalam pill.
    val tabWidths = remember { mutableStateListOf(0, 0, 0) }

    // Track kaca (API 33+) / frosted solid (fallback). Lebar TETAP = PillWidth.
    val baseTrackModifier = modifier
        .width(PillWidth)
        .height(FloatingButtonSize)
        .clip(CircleShape)
    val trackModifier = if (componentStyle.drawsBackdrop()) {
        baseTrackModifier.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                // GLASS = blur + lens (liquid glass). FROSTED = KEDUANYA off ->
                // hanya vibrancy + veil haze (kabut), tanpa distorsi & tanpa artefak.
                if (componentStyle.usesBlur()) {
                    blur(GlassBlurRadius.toPx())
                }
                if (componentStyle.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        // FROSTED: tint translusen. SOLID: track tone rendah (opaque) supaya chip
        // terpilih (surfaceContainerHighest) menonjol tegas di atasnya.
        val trackFill = if (componentStyle == ComponentStyle.FROSTED) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        baseTrackModifier.background(trackFill)
    }

    // Geser konten supaya PUSAT tab terpilih pas di tengah pill. Untuk tab
    // tengah (Gallery) otomatis simetris; ScrollState clamp ke [0, maxScroll].
    LaunchedEffect(selectedIndex, tabWidths.toList()) {
        if (tabWidths.all { it > 0 }) {
            val viewportPx = with(density) { PillWidth.toPx() }
            val gapPx = with(density) { TabGap.toPx() }
            // Pusat X tab terpilih (konten mulai dari 0, TANPA spacer tepi).
            var center = 0f
            for (j in 0 until selectedIndex) center += tabWidths[j] + gapPx
            center += tabWidths[selectedIndex] / 2f
            // Geser supaya pusat tab di tengah pill. ScrollState clamp ke
            // [0, maxScroll] -> tab 1 mentok kiri, tab 3 mentok kanan.
            val target = (center - viewportPx / 2f).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
        }
    }

    // Chip terpilih yang MELUNCUR (slide) dari tab A -> B, bukan muncul/hilang.
    // Posisi X & lebarnya beranimasi mengikuti tab terpilih, dalam koordinat
    // konten pill yang SAMA dgn baris teks (jadi tetap presisi walau pill scroll).
    val measured = tabWidths.all { it > 0 }
    val insetPx = with(density) { SelectionInset.toPx() }
    val gapPx2 = with(density) { TabGap.toPx() }
    var chipLeftPx = 0f
    for (j in 0 until selectedIndex) chipLeftPx += tabWidths[j] + gapPx2
    val targetChipX = (chipLeftPx + insetPx).toInt()
    val targetChipWidth = (tabWidths[selectedIndex] - insetPx * 2f).toInt().coerceAtLeast(0)

    // Snap ke posisi awal (biar chip tak "tumbuh" dari kiri saat pertama tampil),
    // baru animasikan perpindahan-perpindahan berikutnya.
    val chipReady = remember { mutableStateOf(false) }
    LaunchedEffect(measured) { if (measured) chipReady.value = true }
    val chipSpec = if (chipReady.value) tween(340, easing = FastOutSlowInEasing) else snap<Int>()
    val chipX by animateIntAsState(targetChipX, chipSpec, label = "tab-chip-x")
    val chipWidth by animateIntAsState(targetChipWidth, chipSpec, label = "tab-chip-w")

    // GESER (drag) horizontal pada bar menu -> gerakkan pager KONTINU (0 -> 0,4
    // dst) lewat callback ke host. Delta mentah diteruskan apa adanya; host yang
    // memetakan ke fraksi halaman & meng-SNAP ke tab terdekat saat jari dilepas.
    // Callback di-snapshot via rememberUpdatedState biar tak stale di pointerInput.
    val onNavBarDragState = rememberUpdatedState(onNavBarDrag)
    val onNavBarDragStartState = rememberUpdatedState(onNavBarDragStart)
    val onNavBarDragEndState = rememberUpdatedState(onNavBarDragEnd)

    Box(
        modifier = trackModifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { onNavBarDragStartState.value() },
                onDragEnd = { onNavBarDragEndState.value() },
                onDragCancel = { onNavBarDragEndState.value() },
            ) { change, dragAmount ->
                onNavBarDragState.value(dragAmount)
                change.consume()
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false),
        ) {
            // Lapisan 1: chip terpilih yang meluncur, DI BELAKANG teks.
            if (measured) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(chipX, 0) }
                        .padding(vertical = SelectionInset)
                        .width(with(density) { chipWidth.toDp() })
                        .fillMaxHeight()
                        .selectedChipModifier(backdrop, componentStyle),
                )
            }
            // Lapisan 2: baris teks tab (klik + pengukuran lebar).
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { index, (tab, label) ->
                    if (index > 0) Spacer(Modifier.width(TabGap))
                    PillSegment(
                        label = label,
                        selected = selectedTab == tab,
                        // onSizeChanged di LUAR padding -> ukur lebar penuh segmen
                        // (termasuk inset) biar perhitungan posisi chip akurat.
                        modifier = Modifier
                            .onSizeChanged { tabWidths[index] = it.width }
                            .padding(SelectionInset),
                    ) { onSelectTab(tab) }
                }
            }
        }
    }
}

/**
 * Satu segmen (teks) di dalam pill tab. Penanda "selected" TIDAK lagi digambar
 * di sini \u2014 kini dipegang oleh satu chip meluncur (lihat [TabSwitcher]) supaya
 * perpindahan A->B terlihat sebagai GERAKAN, bukan muncul/hilang. Segmen ini
 * hanya mengurus teks (warna beranimasi), area klik, & pengukuran lebar.
 */
@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(260),
        label = "segment-fg",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            // Halo tipis (Shadow blur, offset 0) = "outline" lembut supaya teks
            // tetap kebaca walau konten di belakang glass warnanya mirip.
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    offset = Offset.Zero,
                    blurRadius = TextHaloBlur,
                ),
            ),
            maxLines = 1,
        )
    }
}

/**
 * Modifier penanda chip terpilih (liquid glass GLASS / frosted / solid) yang
 * dipakai oleh chip meluncur di [TabSwitcher].
 */
@Composable
private fun Modifier.selectedChipModifier(
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
): Modifier {
    val selectedGlassTint =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (componentStyle == ComponentStyle.FROSTED) {
                SelectedFrostedHazeAlpha
            } else {
                SelectedGlassTintAlpha
            },
        )
    val selectedFrosted =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedFrostedAlpha)
    // SOLID terpilih: tone paling terang & OPAQUE -> kontras tegas di atas track.
    val selectedSolid = MaterialTheme.colorScheme.surfaceContainerHighest

    return when {
        componentStyle.drawsBackdrop() -> this.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                if (componentStyle.usesBlur()) {
                    blur(GlassBlurRadius.toPx())
                }
                if (componentStyle.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(selectedGlassTint) },
        )
        else -> this.clip(CircleShape).background(
            if (componentStyle == ComponentStyle.FROSTED) selectedFrosted else selectedSolid,
        )
    }
}

/**
 * Icon dengan halo/outline tipis di belakangnya (duplikat icon di-blur, warna
 * surface) supaya tetap terbaca di atas liquid glass walau warna konten di
 * belakang mirip. Blur = no-op di bawah API 31 (fallback tombol lebih pekat).
 */
@Composable
private fun HaloIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center) {
        // Lapisan halo: icon sama, warna surface, di-blur & boleh meluber
        // keluar batas (Unbounded) biar bikin "garis tepi" lembut.
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = modifier.blur(
                radius = IconHaloBlur,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            ),
        )
        // Lapisan utama: icon asli dengan warna konten tombol.
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = modifier,
        )
    }
}

/**
 * Tombol lingkaran mengambang.
 *
 * - API 33+ : liquid glass (Kyant `drawBackdrop` + blur + lens refraction),
 *   membiaskan konten grid di belakangnya.
 * - API < 33: fallback Surface tonal semi-transparan (frosted solid).
 */
@Composable
private fun CircularFloatingButton(
    onClick: () -> Unit,
    contentDescription: String,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    glassTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (componentStyle.drawsBackdrop()) {
        Box(
            modifier = modifier
                .size(FloatingButtonSize)
                .clip(CircleShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        // GLASS = blur (tanpa lens di tombol bulat, karena refraction
                        // Kyant bikin artefak "hexagon" di elemen kecil). FROSTED =
                        // TANPA blur juga -> hanya veil haze, jadi TAK ada hexagon.
                        if (componentStyle.usesBlur()) {
                            blur(GlassBlurRadius.toPx())
                        }
                    },
                    onDrawSurface = { drawRect(glassTint) },
                )
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                content()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (componentStyle == ComponentStyle.FROSTED) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = modifier
                .size(FloatingButtonSize)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
