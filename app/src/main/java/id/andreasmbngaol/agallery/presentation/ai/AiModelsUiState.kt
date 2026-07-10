package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.DeviceCapability
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the AI models management screen: the list of model rows plus the
 * theme values that drive the glass/edge-effect chrome (mirrors the pattern used
 * by the QR generator screen).
 *
 * @property rows one entry per background-removal model, in display order.
 * @property upscaleRows one entry per image-upscaling model, in display order.
 * @property faceRestoreRows one entry per face-restoration model, in display order.
 * @property componentStyleChosen the user's chosen component style, or null while
 *   settings load.
 * @property edgeEffectMode the user's chosen edge-effect mode, or null while
 *   settings load.
 * @property deviceCapability the measured device snapshot used to rate model
 *   suitability, or null while the benchmark runs.
 */
data class AiModelsUiState(
    val rows: List<AiModelRow> = emptyList(),
    val upscaleRows: List<AiModelRow> = emptyList(),
    val faceRestoreRows: List<AiModelRow> = emptyList(),
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
    val deviceCapability: DeviceCapability? = null,
    val liftModelId: String? = null,
    val liftQuality: RemovalQuality = RemovalQuality.ECO,
)
