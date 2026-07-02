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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Gear
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
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder

/**
 * Dua tab utama root screen di AGallery.
 */
enum class GalleryTab { Gallery, Albums }

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
// Fallback API < 33 (tanpa RuntimeShader): container solid semi-transparan.
private const val FrostedFallbackAlpha = 0.7f
// Fallback API < 33 untuk segmen terpilih: hampir solid supaya jelas.
private const val SelectedFrostedAlpha = 0.85f

/**
 * Scaffold screen root (Gallery/Albums): konten full-screen + bar mengambang
 * di bawah berisi (Settings) [ Gallery | Albums ] (Sort), dibagi SpaceEvenly.
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
    onOpenSettings: () -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val liquidGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // Konten (grid) jadi SUMBER backdrop yang dibiaskan bar kaca.
        val contentModifier =
            if (liquidGlassSupported) {
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
                onOpenSettings = onOpenSettings,
                sortOrder = sortOrder,
                onToggleSort = onToggleSort,
                backdrop = backdrop,
                liquidGlassSupported = liquidGlassSupported,
            )
        }
    }
}

@Composable
private fun FloatingTabBar(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    onOpenSettings: () -> Unit,
    sortOrder: GallerySortOrder,
    onToggleSort: () -> Unit,
    backdrop: Backdrop,
    liquidGlassSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = GlassTintAlpha)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularFloatingButton(
            onClick = onOpenSettings,
            contentDescription = "Settings",
            backdrop = backdrop,
            liquidGlassSupported = liquidGlassSupported,
            glassTint = glassTint,
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.Gear,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }

        TabSwitcher(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            backdrop = backdrop,
            liquidGlassSupported = liquidGlassSupported,
            glassTint = glassTint,
        )

        CircularFloatingButton(
            onClick = onToggleSort,
            contentDescription = "Change sort order",
            backdrop = backdrop,
            liquidGlassSupported = liquidGlassSupported,
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
                Icon(
                    imageVector = when (order) {
                        GallerySortOrder.DateDesc -> PhosphorIcons.Regular.SortDescending
                        GallerySortOrder.DateAsc -> PhosphorIcons.Regular.SortAscending
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Switcher tab: pill segmented custom (Gallery | Albums) dengan background
 * liquid glass. Segmen terpilih adalah "chip" kaca yang lebih pekat di atas
 * track kaca (bukan warna primary).
 */
@Composable
private fun TabSwitcher(
    selectedTab: GalleryTab,
    onSelectTab: (GalleryTab) -> Unit,
    backdrop: Backdrop,
    liquidGlassSupported: Boolean,
    glassTint: Color,
    modifier: Modifier = Modifier,
) {
    if (liquidGlassSupported) {
        Row(
            modifier = modifier
                .height(FloatingButtonSize)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(GlassBlurRadius.toPx())
                        lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                    },
                    onDrawSurface = { drawRect(glassTint) },
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillSegment("Gallery", selectedTab == GalleryTab.Gallery, backdrop, true) { onSelectTab(GalleryTab.Gallery) }
            PillSegment("Albums", selectedTab == GalleryTab.Albums, backdrop, true) { onSelectTab(GalleryTab.Albums) }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = modifier.height(FloatingButtonSize),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillSegment("Gallery", selectedTab == GalleryTab.Gallery, backdrop, false) { onSelectTab(GalleryTab.Gallery) }
                PillSegment("Albums", selectedTab == GalleryTab.Albums, backdrop, false) { onSelectTab(GalleryTab.Albums) }
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
    liquidGlassSupported: Boolean,
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

    // Modifier penanda selected: kaca lebih pekat (API 33+) atau fill frosted.
    val selectionModifier =
        when {
            !selected -> Modifier
            liquidGlassSupported -> Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(GlassBlurRadius.toPx())
                    lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                },
                onDrawSurface = { drawRect(selectedGlassTint) },
            )
            else -> Modifier.clip(CircleShape).background(selectedFrosted)
        }

    Box(
        modifier = Modifier
            // fillMaxHeight -> chip mengisi tinggi area konten track (52 - 8
            // padding = 44dp). Inset 4dp jadi seragam di semua sisi, dan radius
            // capsule chip (22) = radius track (26) - inset (4) => corner
            // konsentris & gap simetris.
            .fillMaxHeight()
            .clip(CircleShape)
            .then(selectionModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
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
    liquidGlassSupported: Boolean,
    glassTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (liquidGlassSupported) {
        Box(
            modifier = modifier
                .size(FloatingButtonSize)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(GlassBlurRadius.toPx())
                        lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha),
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
