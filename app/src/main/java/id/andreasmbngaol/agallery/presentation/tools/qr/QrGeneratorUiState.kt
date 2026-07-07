package id.andreasmbngaol.agallery.presentation.tools.qr

import androidx.compose.ui.graphics.ImageBitmap
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.qr.QrCardConfig

/**
 * UI state for the QR Generator screen: card configuration + logo bitmap (when a
 * photo is used) + component style & edge-effect mode (read from settings so the
 * buttons/top bar stay consistent Solid/Frosted/Glass with the other screens).
 */
data class QrGeneratorUiState(
    val config: QrCardConfig = QrCardConfig(),
    val logoBitmap: ImageBitmap? = null,
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
)
