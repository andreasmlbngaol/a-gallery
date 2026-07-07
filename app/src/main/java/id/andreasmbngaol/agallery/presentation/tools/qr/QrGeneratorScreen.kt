package id.andreasmbngaol.agallery.presentation.tools.qr

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.Image
import com.adamglin.phosphoricons.bold.ImageSquare
import com.adamglin.phosphoricons.bold.MapPin
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.X
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.qr.QrEncoder
import id.andreasmbngaol.agallery.core.ui.SystemBarScrim
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveComponentStyle
import id.andreasmbngaol.agallery.core.ui.rememberEffectiveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.qr.QrAltMode
import id.andreasmbngaol.agallery.domain.model.qr.QrBuiltInLogo
import id.andreasmbngaol.agallery.domain.model.qr.QrCardConfig
import id.andreasmbngaol.agallery.domain.model.qr.QrDotStyle
import id.andreasmbngaol.agallery.domain.model.qr.QrExportTarget
import id.andreasmbngaol.agallery.domain.model.qr.QrLogo
import id.andreasmbngaol.agallery.presentation.viewer.GlassIconButton
import id.andreasmbngaol.agallery.presentation.viewer.GlassIsland
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Layar QR Code Generator (fitur 1.6.0). Editor kartu QR yang fully
 * customizable: Title / Subtitle / QR / Alt text / Supporting text; gaya modul
 * (Kotak/Titik); logo tengah (foto dari picker atau ikon bawaan).
 * Hasil bisa disimpan ke galeri atau dibagikan -- baik kartu penuh maupun QR
 * saja -- dgn menangkap Composable ke bitmap lewat GraphicsLayer.
 *
 * Styling topbar & tombol mengikuti Solid/Frosted/Glass ([GlassIconButton] /
 * [GlassIsland]) konsisten dgn layar lain. Edge effect (Off/Darken/Blur)
 * ditangani [SystemBarScrim] lewat mode dari setting.
 */
@Composable
fun QrGeneratorScreen(
    onBack: () -> Unit,
) {
    val viewModel: QrGeneratorViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val config = state.config
    // State picker logo internal berbasis album.
    val pickerAlbums by viewModel.albums.collectAsState()
    val pickerAlbumMedia by viewModel.albumMedia.collectAsState()
    val pickerLoading by viewModel.pickerLoading.collectAsState()
    val componentStyle = rememberEffectiveComponentStyle(state.componentStyleChosen)
    val effectiveMode = rememberEffectiveEdgeEffectMode(state.edgeEffectMode)
    val backdrop = rememberLayerBackdrop()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Resource di-resolve di scope composable (bukan context.getString di dalam
    // lambda) supaya bebas dari lint "Querying resource values using
    // LocalContext.current" & tetap reaktif thd perubahan bahasa/konfigurasi.
    val needContentMsg = stringResource(R.string.qr_need_content)
    val savedMsg = stringResource(R.string.qr_saved)
    val saveFailedMsg = stringResource(R.string.qr_save_failed)
    val shareTitle = stringResource(R.string.qr_share_title)

    var exportTarget by remember { mutableStateOf(QrExportTarget.CARD) }

    // Layer terpisah utk menangkap kartu penuh vs QR saja.
    val cardLayer = rememberGraphicsLayer()
    val qrLayer = rememberGraphicsLayer()

    // Picker foto INTERNAL (bukan photo-picker sistem). Dibuka via dialog di
    // bawah; daftar foto di-load lazy dari galeri lewat ViewModel.
    var showPhotoPicker by remember { mutableStateOf(false) }

    val hasContent = config.content.isNotBlank()

    fun captureLayer(): GraphicsLayer =
        if (exportTarget == QrExportTarget.QR_ONLY) qrLayer else cardLayer

    fun onSave() {
        if (!hasContent) {
            Toast.makeText(context, needContentMsg, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val bitmap = captureLayer().toImageBitmap().asAndroidBitmap()
            val ok = viewModel.saveToGallery(bitmap)
            Toast.makeText(
                context,
                if (ok) savedMsg else saveFailedMsg,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onShare() {
        if (!hasContent) {
            Toast.makeText(context, needContentMsg, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val bitmap = captureLayer().toImageBitmap().asAndroidBitmap()
            val uri = viewModel.buildShareUri(bitmap) ?: return@launch
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, shareTitle),
            )
        }
    }

    SystemBarScrim(
        mode = effectiveMode,
        topOverlay = {
            // ---------- Top bar (glass back + title) ----------
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
                    onClick = onBack,
                    contentDescription = stringResource(R.string.action_back),
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
                Text(
                    text = stringResource(R.string.tool_qr_generator_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ---------- Action island (icon-only: Simpan · Bagikan · Clear) ----------
            GlassIsland(
                style = componentStyle,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            ) {
                IconButton(onClick = { onSave() }) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ImageSquare,
                        contentDescription = stringResource(R.string.qr_action_save),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { onShare() }) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ShareNetwork,
                        contentDescription = stringResource(R.string.qr_action_share),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Clear all: reset semua input. State ViewModel bertahan selama
                // layar hidup (nggak ke-reset walau keluar-masuk lewat back),
                // jadi perlu tombol reset eksplisit -- kini di island bawah.
                IconButton(onClick = viewModel::clearAll) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowClockwise,
                        contentDescription = stringResource(R.string.qr_action_clear),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { sourceModifier ->
        val backdropModifier = if (componentStyle.drawsBackdrop()) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = backdropModifier
                .then(sourceModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 96.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            QrCardPreview(
                config = config,
                logoBitmap = (config.logo as? QrLogo.Photo)?.let { state.logoBitmap },
                logoIcon = (config.logo as? QrLogo.BuiltIn)?.let { builtInLogoIcon(it.logo) },
                cardLayer = cardLayer,
                qrLayer = qrLayer,
            )

            QrSectionHeader(stringResource(R.string.qr_section_content))
            // Content = data mentah QR (tak tampil di kartu), jadi tanpa gaya.
            QrField(config.content, viewModel::updateContent, stringResource(R.string.qr_field_content_label))
            // Field yg tampil di kartu: teks + ukuran (sp) + color picker.
            QrStyledField(
                value = config.title,
                onValueChange = viewModel::updateTitle,
                label = stringResource(R.string.qr_field_title_label),
                size = config.titleSize,
                onSizeChange = viewModel::setTitleSize,
                color = config.titleColor,
                onColorChange = viewModel::setTitleColor,
            )
            QrStyledField(
                value = config.subtitle,
                onValueChange = viewModel::updateSubtitle,
                label = stringResource(R.string.qr_field_subtitle_label),
                size = config.subtitleSize,
                onSizeChange = viewModel::setSubtitleSize,
                color = config.subtitleColor,
                onColorChange = viewModel::setSubtitleColor,
            )

            // Alt text: bisa "ikut konten" (nggak usah ketik ulang) atau custom.
            QrSectionHeader(stringResource(R.string.qr_field_alt_label))
            QrSegmented(
                options = listOf(
                    QrAltMode.SAME to stringResource(R.string.qr_alt_same),
                    QrAltMode.CUSTOM to stringResource(R.string.qr_alt_custom),
                ),
                selected = config.altMode,
                onSelect = viewModel::setAltMode,
            )
            QrStyledField(
                value = if (config.altMode == QrAltMode.SAME) config.content else config.altText,
                onValueChange = viewModel::updateAltText,
                label = stringResource(R.string.qr_field_alt_label),
                size = config.altSize,
                onSizeChange = viewModel::setAltSize,
                color = config.altColor,
                onColorChange = viewModel::setAltColor,
                textEnabled = config.altMode == QrAltMode.CUSTOM,
            )

            QrStyledField(
                value = config.supportingText,
                onValueChange = viewModel::updateSupporting,
                label = stringResource(R.string.qr_field_supporting_label),
                size = config.supportingSize,
                onSizeChange = viewModel::setSupportingSize,
                color = config.supportingColor,
                onColorChange = viewModel::setSupportingColor,
            )

            QrSectionHeader(stringResource(R.string.qr_section_style))
            QrSegmented(
                options = listOf(
                    QrDotStyle.SQUARE to stringResource(R.string.qr_style_square),
                    QrDotStyle.DOTS to stringResource(R.string.qr_style_dots),
                ),
                selected = config.dotStyle,
                onSelect = viewModel::setDotStyle,
            )

            QrSectionHeader(stringResource(R.string.qr_section_logo))
            QrLogoPicker(
                logo = config.logo,
                onNone = viewModel::clearLogo,
                onBuiltIn = viewModel::setBuiltInLogo,
                onPickPhoto = {
                    viewModel.loadAlbums()
                    showPhotoPicker = true
                },
            )

            QrSectionHeader(stringResource(R.string.qr_section_export))
            QrSegmented(
                options = listOf(
                    QrExportTarget.CARD to stringResource(R.string.qr_export_card),
                    QrExportTarget.QR_ONLY to stringResource(R.string.qr_export_qr_only),
                ),
                selected = exportTarget,
                onSelect = { exportTarget = it },
            )
        }

        if (showPhotoPicker) {
            QrInternalPhotoPickerDialog(
                albums = pickerAlbums,
                albumMedia = pickerAlbumMedia,
                loading = pickerLoading,
                onOpenAlbum = viewModel::openAlbum,
                onCloseAlbum = viewModel::closeAlbumMedia,
                onPick = { uri ->
                    viewModel.setPhotoLogo(uri)
                    showPhotoPicker = false
                },
                onDismiss = { showPhotoPicker = false },
            )
        }
    }
}

/** Kartu QR yang dirender (juga di-capture ke bitmap saat Simpan/Bagikan). */
@Composable
private fun QrCardPreview(
    config: QrCardConfig,
    logoBitmap: ImageBitmap?,
    logoIcon: ImageVector?,
    cardLayer: GraphicsLayer,
    qrLayer: GraphicsLayer,
) {
    val matrix = remember(config.content) { QrEncoder.encode(config.content) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                // Rekam ke layer pada resolusi DITINGKATKAN utk ekspor tajam,
                // tapi preview tetap tampil di resolusi native layar (drawContent
                // langsung). Ini menghilangkan hasil PNG yang burik/pecah.
                val scale = exportScaleFor(size.width)
                cardLayer.record(
                    size = IntSize(
                        (size.width * scale).roundToInt(),
                        (size.height * scale).roundToInt(),
                    ),
                ) {
                    scale(scale = scale, pivot = Offset.Zero) {
                        this@drawWithContent.drawContent()
                    }
                }
                drawContent()
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (config.title.isNotBlank()) {
            Text(
                text = config.title,
                fontSize = config.titleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(config.titleColor),
                textAlign = TextAlign.Center,
            )
        }
        if (config.subtitle.isNotBlank()) {
            Text(
                text = config.subtitle,
                fontSize = config.subtitleSize.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(config.subtitleColor),
                textAlign = TextAlign.Center,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .drawWithContent {
                    val scale = exportScaleFor(size.width)
                    qrLayer.record(
                        size = IntSize(
                            (size.width * scale).roundToInt(),
                            (size.height * scale).roundToInt(),
                        ),
                    ) {
                        scale(scale = scale, pivot = Offset.Zero) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawContent()
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (matrix != null) {
                QrCodeView(
                    matrix = matrix,
                    dotStyle = config.dotStyle,
                    darkColor = Color(0xFF111111),
                    lightColor = Color.White,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    logoBitmap = logoBitmap,
                    logoIcon = logoIcon,
                )
            } else {
                Text(
                    text = stringResource(R.string.qr_preview_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        val effectiveAlt = if (config.altMode == QrAltMode.SAME) config.content else config.altText
        if (effectiveAlt.isNotBlank()) {
            Text(
                text = effectiveAlt,
                fontSize = config.altSize.sp,
                fontWeight = FontWeight.Medium,
                color = Color(config.altColor),
                textAlign = TextAlign.Center,
            )
        }
        if (config.supportingText.isNotBlank()) {
            Text(
                text = config.supportingText,
                fontSize = config.supportingSize.sp,
                color = Color(config.supportingColor),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun QrSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun QrField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Field teks + kontrol gaya di sampingnya: ukuran (sp) & color picker. Dipakai
 * utk Title / Subtitle / Alt / Supporting. [textEnabled] dimatikan saat Alt
 * pakai mode "ikut konten" -- teks dikunci, tapi ukuran & warna tetap bisa
 * diatur.
 */
@Composable
private fun QrStyledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    size: Float,
    onSizeChange: (Float) -> Unit,
    color: Long,
    onColorChange: (Long) -> Unit,
    textEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = textEnabled,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
        QrSizeField(size = size, onSizeChange = onSizeChange)
        QrColorSwatch(color = color, onColorChange = onColorChange)
    }
}

private const val QR_SIZE_MIN = 4
private const val QR_SIZE_MAX = 99

/**
 * Field angka kompak utk ukuran teks (sp). Teksnya disimpan sbg String bebas
 * supaya user bisa menghapus sampai kosong lalu mengetik ulang (mis. 24 ->
 * hapus -> 7) tanpa "nyangkut" di nilai minimum. Nilai diteruskan HANYA saat
 * valid & dalam rentang; kosong / di luar rentang dibiarkan (ukuran terakhir
 * dipertahankan, tidak dipaksa balik).
 */
@Composable
private fun QrSizeField(
    size: Float,
    onSizeChange: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(size.roundToInt().toString()) }
    // Lacak nilai terakhir yg KITA kirim supaya perubahan `size` dari luar
    // (mis. Clear all) me-resync teks, tapi ketikan sendiri tidak ikut ter-reset.
    var lastPushed by remember { mutableStateOf(size) }
    if (size != lastPushed) {
        text = size.roundToInt().toString()
        lastPushed = size
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(2)
            text = digits
            val n = digits.toIntOrNull()
            if (n != null && n in QR_SIZE_MIN..QR_SIZE_MAX) {
                lastPushed = n.toFloat()
                onSizeChange(n.toFloat())
            }
        },
        singleLine = true,
        suffix = { Text("sp") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(96.dp),
    )
}

/** Swatch warna -> buka color picker fleksibel (HSV + HEX + preset). */
@Composable
private fun QrColorSwatch(
    color: Long,
    onColorChange: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.qr_color_pick)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClickLabel = pickLabel) { open = true },
    )
    if (open) {
        QrColorPickerDialog(
            initial = color,
            onDismiss = { open = false },
            onConfirm = {
                onColorChange(it)
                open = false
            },
        )
    }
}

/**
 * Color picker fleksibel dibangun sendiri (tanpa dependency eksternal): panel
 * Saturation x Value, slider Hue, input HEX (#RRGGBB), plus baris preset cepat.
 * Warna dikembalikan sbg Long ARGB (0xFFRRGGBB, alpha penuh).
 */
@Composable
private fun QrColorPickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initHsv = remember { argbLongToHsv(initial) }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var value by remember { mutableStateOf(initHsv[2]) }
    var hexText by remember { mutableStateOf(hexOf(initial)) }

    val current = hsvToArgbLong(hue, sat, value)

    fun syncHexFromHsv() {
        hexText = hexOf(hsvToArgbLong(hue, sat, value))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.qr_color_pick),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Panel Saturation (sumbu X) x Value (sumbu Y).
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = (1f - o.y / size.height).coerceIn(0f, 1f)
                                syncHexFromHsv()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                syncHexFromHsv()
                            }
                        },
                ) {
                    drawRect(Color.hsv(hue, 1f, 1f))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val cx = sat * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(Color.Black, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 4f))
                    drawCircle(Color.White, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 2f))
                }

                // Slider Hue (0..360).
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                hue = (o.x / size.width * 360f).coerceIn(0f, 360f)
                                syncHexFromHsv()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                syncHexFromHsv()
                            }
                        },
                ) {
                    val stops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
                        .map { Color.hsv(it, 1f, 1f) }
                    drawRect(Brush.horizontalGradient(stops))
                    val x = (hue / 360f) * size.width
                    drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), strokeWidth = 5f)
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                }

                // Input HEX + preview warna terkini.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val cleaned = raw.removePrefix("#")
                                .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                                .take(6)
                                .uppercase()
                            hexText = cleaned
                            parseHex(cleaned)?.let { parsed ->
                                val hsv = argbLongToHsv(parsed)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text(stringResource(R.string.qr_color_hex)) },
                        prefix = { Text("#") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(current))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }

                // Preset cepat.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QR_PALETTE.chunked(5).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                        .clickable {
                                            val hsv = argbLongToHsv(c)
                                            hue = hsv[0]
                                            sat = hsv[1]
                                            value = hsv[2]
                                            hexText = hexOf(c)
                                        },
                                )
                            }
                        }
                    }
                }

                // Aksi.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.qr_color_cancel))
                    }
                    TextButton(onClick = { onConfirm(current) }) {
                        Text(stringResource(R.string.qr_color_apply))
                    }
                }
            }
        }
    }
}

// Palet preset color picker (ARGB 0xAARRGGBB). Baris 1 = netral (incl. default).
private val QR_PALETTE = listOf(
    0xFF0A0A0A, 0xFF3A3A3A, 0xFF777777, 0xFFBDBDBD, 0xFFFFFFFF,
    0xFFB00020, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00897B, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60,
)

// --- Helper konversi warna (Long ARGB 0xFFRRGGBB <-> HSV <-> HEX) ---

private fun hsvToArgbLong(h: Float, s: Float, v: Float): Long {
    val argb = Color.hsv(h, s, v).toArgb()
    return 0xFF000000L or (argb.toLong() and 0xFFFFFF)
}

private fun argbLongToHsv(color: Long): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toInt(), hsv)
    return hsv
}

private fun hexOf(color: Long): String =
    "%06X".format(color.toInt() and 0xFFFFFF)

private fun parseHex(hex: String): Long? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val rgb = clean.toLongOrNull(16) ?: return null
    return 0xFF000000L or (rgb and 0xFFFFFF)
}

/** Segmented control ringan (dipakai utk gaya modul & target ekspor). */
@Composable
private fun <T> QrSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun QrLogoPicker(
    logo: QrLogo,
    onNone: () -> Unit,
    onBuiltIn: (QrBuiltInLogo) -> Unit,
    onPickPhoto: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoChip(selected = logo is QrLogo.None, onClick = onNone) {
            Icon(
                imageVector = PhosphorIcons.Bold.X,
                contentDescription = stringResource(R.string.qr_logo_none),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Foto (upload) ditaruh tepat setelah "No logo" sbg pilihan utama.
        LogoChip(selected = logo is QrLogo.Photo, onClick = onPickPhoto) {
            Icon(
                imageVector = PhosphorIcons.Bold.Image,
                contentDescription = stringResource(R.string.qr_logo_photo),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Ikon bawaan yang "bermakna" utk ditaruh di tengah (Hati, Lokasi).
        QrBuiltInLogo.entries.forEach { builtIn ->
            LogoChip(
                selected = logo is QrLogo.BuiltIn && logo.logo == builtIn,
                onClick = { onBuiltIn(builtIn) },
            ) {
                Icon(
                    imageVector = builtInLogoIcon(builtIn),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LogoChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                },
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun builtInLogoIcon(logo: QrBuiltInLogo): ImageVector = when (logo) {
    QrBuiltInLogo.HEART -> PhosphorIcons.Bold.Heart
    QrBuiltInLogo.LOCATION -> PhosphorIcons.Bold.MapPin
}

// Skala ekspor: perbesar tangkapan supaya PNG hasil tajam (target ~2048px pada
// sisi terlebar), dibatasi 1x..4x supaya tidak boros memori di layar besar.
private const val EXPORT_TARGET_PX = 2048f

private fun exportScaleFor(widthPx: Float): Float =
    if (widthPx <= 0f) 1f else (EXPORT_TARGET_PX / widthPx).coerceIn(1f, 4f)

/**
 * Picker logo INTERNAL berbasis ALBUM (bukan photo-picker sistem). Alur meniru
 * "Create new album": tampilkan daftar folder/album dulu (grid 3 kolom), tap
 * satu album utk melihat foto di dalamnya, lalu tap satu foto -> langsung jadi
 * logo lewat [onPick]. Tombol Back device atau panah di header kembali dari
 * isi album ke daftar album.
 */
@Composable
private fun QrInternalPhotoPickerDialog(
    albums: List<Album>,
    albumMedia: List<MediaItem>,
    loading: Boolean,
    onOpenAlbum: (MediaScope) -> Unit,
    onCloseAlbum: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var openedAlbum by remember { mutableStateOf<Album?>(null) }

    fun backToAlbums() {
        openedAlbum = null
        onCloseAlbum()
    }

    // Back device: dari isi album -> daftar album; dari daftar album -> tutup
    // (dibiarkan ke perilaku default Dialog).
    BackHandler(enabled = openedAlbum != null) { backToAlbums() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (openedAlbum != null) {
                        IconButton(onClick = { backToAlbums() }) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = openedAlbum?.name ?: stringResource(R.string.qr_logo_pick_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        openedAlbum == null -> {
                            if (albums.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.qr_logo_pick_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(albums, key = { it.key }) { album ->
                                        QrPickerAlbumTile(
                                            album = album,
                                            onClick = {
                                                openedAlbum = album
                                                onOpenAlbum(album.scope)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            if (albumMedia.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.qr_logo_pick_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(albumMedia, key = { it.id }) { item ->
                                        QrPickerPhotoCell(
                                            item = item,
                                            onClick = { onPick(item.uri) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

/** Kartu album (cover + nama + jumlah item) utk langkah pertama picker logo. */
@Composable
private fun QrPickerAlbumTile(
    album: Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.album_item_count, album.itemCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Sel foto (single-tap = pilih) utk langkah kedua picker logo. */
@Composable
private fun QrPickerPhotoCell(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
