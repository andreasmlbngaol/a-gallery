package id.andreasmbngaol.agallery.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Tinggi visual topbar antar-layar tab (Settings & Albums). Disamakan dengan
 * TopAppBar Gallery (72dp) supaya seragam.
 */
val ScreenTopBarHeight = 72.dp

/**
 * Scaffold topbar seragam untuk layar tab (Settings & Albums) yang meniru
 * topbar Gallery: judul 26sp SemiBold TRANSPARAN yang digambar DI ATAS
 * [SystemBarScrim]. Karena memakai SystemBarScrim yang sama, efek tepi yang
 * dipilih di Settings (Off / Darken / Blurry) IKUT diterapkan pada area topbar
 * (dan navigation bar) layar ini \u2014 persis seperti di Gallery.
 *
 * [content] menerima `contentModifier` yang WAJIB dipasang ke elemen konten
 * (idealnya yang scrollable) supaya mode BLURRY bisa mengambil sumber blur
 * (hazeSource). Konten juga harus diberi padding atas minimal status bar +
 * [ScreenTopBarHeight] agar tidak tertutup topbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeEffectTopBarScaffold(
    title: String,
    edgeEffectMode: EdgeEffectMode?,
    modifier: Modifier = Modifier,
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    val effectiveMode = rememberEffectiveEdgeEffectMode(edgeEffectMode)
    SystemBarScrim(
        mode = effectiveMode,
        modifier = modifier,
        topExtraHeight = ScreenTopBarHeight,
        topOverlay = { ScreenTopBarTitle(title) },
        content = content,
    )
}

/** Judul topbar transparan (crisp di atas scrim), identik gaya dengan Gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ScreenTopBarTitle(title: String) {
    TopAppBar(
        modifier = Modifier.align(Alignment.TopCenter),
        title = {
            Text(
                text = title,
                style = LocalTextStyle.current.copy(
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f,
                    ),
                ),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        windowInsets = WindowInsets.statusBars,
    )
}
