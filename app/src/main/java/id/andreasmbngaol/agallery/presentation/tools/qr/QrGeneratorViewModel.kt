package id.andreasmbngaol.agallery.presentation.tools.qr

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.data.qr.QrImageSaver
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.QrAltMode
import id.andreasmbngaol.agallery.domain.model.QrBuiltInLogo
import id.andreasmbngaol.agallery.domain.model.QrCardConfig
import id.andreasmbngaol.agallery.domain.model.QrDotStyle
import id.andreasmbngaol.agallery.domain.model.QrLogo
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State layar QR Generator: konfigurasi kartu + bitmap logo (kalau foto) +
 * gaya komponen & edge effect (dibaca dari settings, biar tombol/topbar
 * konsisten Solid/Frosted/Glass dgn layar lain).
 */
data class QrGeneratorUiState(
    val config: QrCardConfig = QrCardConfig(),
    val logoBitmap: ImageBitmap? = null,
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
)

class QrGeneratorViewModel(
    getSettings: GetSettingsUseCase,
    private val imageSaver: QrImageSaver,
) : ViewModel() {

    private val _config = MutableStateFlow(QrCardConfig())
    // Bitmap logo hasil decode foto (transient, tidak masuk domain config).
    private val _logoBitmap = MutableStateFlow<ImageBitmap?>(null)

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    val uiState: StateFlow<QrGeneratorUiState> =
        combine(_config, _logoBitmap, settings) { config, logo, s ->
            QrGeneratorUiState(
                config = config,
                logoBitmap = logo,
                componentStyleChosen = s.componentStyle,
                edgeEffectMode = s.edgeEffectMode,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            QrGeneratorUiState(),
        )

    // Alt text kini punya mode SAME/CUSTOM, jadi content tak perlu lagi
    // "nyetir" altText otomatis.
    fun updateContent(value: String) = _config.update { it.copy(content = value) }

    fun updateTitle(value: String) = _config.update { it.copy(title = value) }
    fun updateSubtitle(value: String) = _config.update { it.copy(subtitle = value) }
    fun updateAltText(value: String) = _config.update { it.copy(altText = value) }
    fun updateSupporting(value: String) = _config.update { it.copy(supportingText = value) }
    fun setDotStyle(style: QrDotStyle) = _config.update { it.copy(dotStyle = style) }

    // --- Per-field styling (ukuran & warna) ---
    fun setTitleSize(size: Float) = _config.update { it.copy(titleSize = size) }
    fun setTitleColor(color: Long) = _config.update { it.copy(titleColor = color) }
    fun setSubtitleSize(size: Float) = _config.update { it.copy(subtitleSize = size) }
    fun setSubtitleColor(color: Long) = _config.update { it.copy(subtitleColor = color) }
    fun setAltSize(size: Float) = _config.update { it.copy(altSize = size) }
    fun setAltColor(color: Long) = _config.update { it.copy(altColor = color) }
    fun setSupportingSize(size: Float) = _config.update { it.copy(supportingSize = size) }
    fun setSupportingColor(color: Long) = _config.update { it.copy(supportingColor = color) }

    /** Mode Alt text: ikut konten (SAME) atau diketik sendiri (CUSTOM). */
    fun setAltMode(mode: QrAltMode) = _config.update { it.copy(altMode = mode) }

    fun clearLogo() {
        _config.update { it.copy(logo = QrLogo.None) }
        _logoBitmap.value = null
    }

    fun setBuiltInLogo(logo: QrBuiltInLogo) {
        _config.update { it.copy(logo = QrLogo.BuiltIn(logo)) }
        _logoBitmap.value = null
    }

    fun setPhotoLogo(uri: String) {
        _config.update { it.copy(logo = QrLogo.Photo(uri)) }
        viewModelScope.launch { _logoBitmap.value = imageSaver.loadLogoBitmap(uri) }
    }

    /**
     * Reset SEMUA input balik ke default. State ViewModel bertahan selama layar
     * hidup (nggak ke-reset walau user keluar-masuk lewat back), jadi ini
     * satu-satunya cara mengosongkan form -- dipicu tombol Clear all di top bar.
     */
    fun clearAll() {
        _config.value = QrCardConfig()
        _logoBitmap.value = null
    }

    /** Simpan bitmap hasil capture ke galeri. Dipanggil dari layar (suspend). */
    suspend fun saveToGallery(bitmap: Bitmap): Boolean = imageSaver.saveToGallery(bitmap)

    /** Siapkan uri share dari bitmap hasil capture. */
    suspend fun buildShareUri(bitmap: Bitmap): Uri? = imageSaver.cacheForShare(bitmap)
}
