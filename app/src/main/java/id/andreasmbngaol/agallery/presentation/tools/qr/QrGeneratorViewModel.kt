package id.andreasmbngaol.agallery.presentation.tools.qr

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.data.qr.QrImageSaver
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import id.andreasmbngaol.agallery.domain.model.qr.QrAltMode
import id.andreasmbngaol.agallery.domain.model.qr.QrBuiltInLogo
import id.andreasmbngaol.agallery.domain.model.qr.QrCardConfig
import id.andreasmbngaol.agallery.domain.model.qr.QrDotStyle
import id.andreasmbngaol.agallery.domain.model.qr.QrLogo
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.usecase.media.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QrGeneratorViewModel(
    getSettings: GetSettingsUseCase,
    private val getAlbums: GetAlbumsUseCase,
    private val getAllMedia: GetAllMediaUseCase,
    private val imageSaver: QrImageSaver,
) : ViewModel() {
    private val _config = MutableStateFlow(QrCardConfig())
    private val _logoBitmap = MutableStateFlow<ImageBitmap?>(null)

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _albumMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val albumMedia: StateFlow<List<MediaItem>> = _albumMedia.asStateFlow()

    private val _pickerLoading = MutableStateFlow(false)
    val pickerLoading: StateFlow<Boolean> = _pickerLoading.asStateFlow()

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

    fun updateContent(value: String) = _config.update { it.copy(content = value) }

    fun updateTitle(value: String) = _config.update { it.copy(title = value) }
    fun updateSubtitle(value: String) = _config.update { it.copy(subtitle = value) }
    fun updateAltText(value: String) = _config.update { it.copy(altText = value) }
    fun updateSupporting(value: String) = _config.update { it.copy(supportingText = value) }
    fun setDotStyle(style: QrDotStyle) = _config.update { it.copy(dotStyle = style) }

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
     * Load the album/folder list for the logo picker. Lazy & one-shot: called when
     * the user opens the picker; if it already has content or is loading, it does
     * nothing.
     */
    fun loadAlbums() {
        if (_albums.value.isNotEmpty() || _pickerLoading.value) return
        _pickerLoading.value = true
        viewModelScope.launch {
            _albums.value = getAlbums().sortedBy { it.name.lowercase() }
            _pickerLoading.value = false
        }
    }

    /** Open a single album -> load the photos (images only) inside it. */
    fun openAlbum(scope: MediaScope) {
        _pickerLoading.value = true
        viewModelScope.launch {
            _albumMedia.value = getAllMedia(GallerySortOrder.DateDesc, scope)
                .filter { it.type == MediaType.IMAGE }
            _pickerLoading.value = false
        }
    }

    /** Return from album content to the album list (clear the temporary media). */
    fun closeAlbumMedia() {
        _albumMedia.value = emptyList()
    }

    /**
     * Reset ALL inputs back to default. The ViewModel state survives while the
     * screen is alive (it is not reset even when the user navigates back and
     * forth), so this is the only way to clear the form -- triggered by the
     * Clear all button in the top bar.
     */
    fun clearAll() {
        _config.value = QrCardConfig()
        _logoBitmap.value = null
    }

    /** Save the captured bitmap to the gallery. Called from the screen (suspend). */
    suspend fun saveToGallery(bitmap: Bitmap): Boolean = imageSaver.saveToGallery(bitmap)

    /** Prepare a share uri from the captured bitmap. */
    suspend fun buildShareUri(bitmap: Bitmap): Uri? = imageSaver.cacheForShare(bitmap)
}
