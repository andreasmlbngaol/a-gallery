package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.regular.SortAscending
import com.adamglin.phosphoricons.regular.SortDescending
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
// Tint tipis di atas kaca (track pill & tombol) supaya tetap tembus pandang.
private const val GlassTintAlpha = 0.22f
// Tint segmen TERPILIH: sengaja lebih opaque/pekat -> kaca lebih "padat",
// jadi penanda selected tanpa keluar dari bahasa liquid glass.
private const val SelectedGlassTintAlpha = 0.55f
// Non-glass: FROSTED lebih translusen (masih berkesan kaca), SOLID hampir opaque.
private const val FrostedFallbackAlpha = 0.55f
private const val SolidFallbackAlpha = 0.95f
// Segmen terpilih untuk masing-masing gaya non-glass.
private const val SelectedFrostedAlpha = 0.8f
private const val SelectedSolidAlpha = 0.98f

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
    onOpenSearch: () -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    componentStyle: ComponentStyle = ComponentStyle.FROSTED,
    // True saat konten (grid) lagi di-scroll -> nav bar FROSTED membekukan capture
    // backdrop biar hemat GPU. Tak berpengaruh untuk GLASS (selalu live) & SOLID.
    contentInteracting: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Kapan konten (grid) di-CAPTURE sebagai sumber backdrop:
    // - GLASS  : selalu live (refraction ngikut gerakan).
    // - FROSTED: dibekukan saat konten di-scroll (contentInteracting) -> hemat
    //   GPU; snapshot terakhir tetap dipakai jadi tetap berkesan kaca.
    // - SOLID / < API 33: TIDAK pernah capture (drawsBackdrop=false) -> grid tak
    //   di-render ulang ke layer (nol overhead, tak render background).
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
                onOpenSearch = onOpenSearch,
                sortOrder = sortOrder,
                onToggleSort = onToggleSort,
                backdrop = backdrop,
                componentStyle = componentStyle,
            )
        }
    }
}

@Composable
private fun FloatingTabBar(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    onOpenSearch: () -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
    modifier: Modifier = Modifier,
) {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = GlassTintAlpha)
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
        // Kiri: action khusus tab. Saat ini hanya tab Gallery yang punya
        // action (Sort). Tab Settings/Albums tidak menampilkan tombol apa pun.
        if (selectedTab == GalleryTab.Gallery) {
            CircularFloatingButton(
                onClick = onToggleSort,
                contentDescription = "Change sort order",
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
                            GallerySortOrder.DateDesc -> PhosphorIcons.Regular.SortDescending
                            GallerySortOrder.DateAsc -> PhosphorIcons.Regular.SortAscending
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Tengah: pill tab (Settings | Gallery | Albums), lebar tetap & selalu
        // di tengah bar. Tab terpilih ditengahkan -> peek kiri/kanan simetris.
        TabSwitcher(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            backdrop = backdrop,
            componentStyle = componentStyle,
            glassTint = glassTint,
        )

        // Kanan: tombol Search (khusus tab Gallery).
        if (selectedTab == GalleryTab.Gallery) {
            CircularFloatingButton(
                onClick = onOpenSearch,
                contentDescription = "Search photos",
                backdrop = backdrop,
                componentStyle = componentStyle,
                glassTint = glassTint,
            ) {
                HaloIcon(
                    imageVector = PhosphorIcons.Regular.MagnifyingGlass,
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
) {
    val entries = listOf(
        GalleryTab.Settings to "Settings",
        GalleryTab.Gallery to "Gallery",
        GalleryTab.Albums to "Albums",
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
                blur(GlassBlurRadius.toPx())
                // Lens (refraction) HANYA GLASS. FROSTED = blur saja = kaca buram,
                // lebih ringan & tanpa artefak pembiasan.
                if (componentStyle.usesLens()) {
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                }
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        baseTrackModifier.background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = if (componentStyle == ComponentStyle.FROSTED) FrostedFallbackAlpha else SolidFallbackAlpha,
            ),
        )
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

    Box(modifier = trackModifier) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEachIndexed { index, (tab, label) ->
                if (index > 0) Spacer(Modifier.width(TabGap))
                PillSegment(
                    label = label,
                    selected = selectedTab == tab,
                    backdrop = backdrop,
                    componentStyle = componentStyle,
                    // padding(4.dp): inset chip selected simetris di semua sisi
                    // (dulu cuma vertikal 4dp, samping rapat). onSizeChanged di
                    // LUAR padding -> ukur lebar penuh biar math center akurat.
                    modifier = Modifier
                        .onSizeChanged { tabWidths[index] = it.width }
                        .padding(4.dp),
                ) { onSelectTab(tab) }
            }
        }
    }
}

/**
 * Satu segmen di dalam pill tab.
 *
 * - Terpilih + API 33+ : "chip" liquid glass yang lebih pekat
 *   ([SelectedGlassTintAlpha]) di atas track.
 * - Terpilih + API < 33: fallback fill frosted hampir solid.
 * - Tidak terpilih     : transparan (menyatu dengan track).
 *
 * Warna teks beranimasi antara `onSurface` (terpilih) dan `onSurfaceVariant`.
 */
@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    backdrop: Backdrop,
    componentStyle: ComponentStyle,
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

    val selectedGlassTint =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedGlassTintAlpha)
    val selectedFrosted =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedFrostedAlpha)
    val selectedSolid =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedSolidAlpha)

    // Modifier penanda selected: liquid glass (GLASS) atau fill frosted/solid.
    val selectionModifier =
        when {
            !selected -> Modifier
            componentStyle.drawsBackdrop() -> Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(GlassBlurRadius.toPx())
                    if (componentStyle.usesLens()) {
                        lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                    }
                },
                onDrawSurface = { drawRect(selectedGlassTint) },
            )
            else -> Modifier.clip(CircleShape).background(
                if (componentStyle == ComponentStyle.FROSTED) selectedFrosted else selectedSolid,
            )
        }

    Box(
        modifier = modifier
            // Chip wrap-content (selebar teks + padding). fillMaxHeight + inset
            // 4dp vertikal (dari pemanggil) -> chip konsentris di track.
            .fillMaxHeight()
            .clip(CircleShape)
            .then(selectionModifier)
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
                        blur(GlassBlurRadius.toPx())
                        // SENGAJA tanpa lens: di tombol bulat kecil, refraction Kyant
                        // bikin artefak tepi (kelihatan seperti hexagon). Blur saja
                        // sudah cukup "kaca" & lebih ringan (berlaku glass & frosted).
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = if (componentStyle == ComponentStyle.FROSTED) FrostedFallbackAlpha else SolidFallbackAlpha,
            ),
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
