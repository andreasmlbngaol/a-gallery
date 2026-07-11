package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A catalog entry describing an AI model the user may import. This is metadata
 * only — no weights are bundled in the app. The user downloads the `.onnx` file
 * themselves from [downloadUrl] and imports it through the system file picker.
 *
 * @property id stable identifier & on-disk file stem.
 * @property feature the capability this model powers.
 * @property tier quality/size positioning badge.
 * @property displayName human-readable model name (a proper noun, not localized).
 * @property approxSizeBytes approximate download size, for display only.
 * @property expectedSizeBytes exact size used for import validation, or 0 to skip
 *   the size check.
 * @property sha256 lowercase-hex checksum used for import validation, or empty to
 *   skip the checksum check.
 * @property io tensor I/O + normalization spec.
 * @property downloadUrl web page where the user can obtain the `.onnx` weights.
 * @property recommended whether this is the suggested default for its feature.
 * @property offersQualityChoice whether this model exposes the per-run
 *   Eco/Balanced/High quality selector. Only true for models with a large,
 *   DYNAMIC input where scaling down gives a real speed/quality trade-off
 *   (e.g. the IS-Net dynamic re-export). Small or fixed-size models leave this
 *   false so the UI hides the selector and they always run at native quality.
 * @property estimatedPeakMemoryBytes rough peak RAM a single run needs (weights
 *   plus native activations), used to advise device suitability before import;
 *   0 skips the check.
 * @property xnnpackEligible whether this model may attempt the XNNPACK execution
 *   provider. Only the Conv-dominated Real-ESRGAN upscalers set this true (their
 *   `Resize` nodes are nearest-mode, which XNNPACK accepts); transformer-heavy
 *   SCUNet/GPEN gain nothing and the background-removal models are blocked by a
 *   bilinear `Resize`, so they stay on the CPU provider (see the 2.4.1 plan).
 */
data class AiModelSpec(
    val id: AiModelId,
    val feature: AiFeature,
    val tier: ModelTier,
    val displayName: String,
    val approxSizeBytes: Long,
    val expectedSizeBytes: Long,
    val sha256: String,
    val io: ModelIoSpec,
    val downloadUrl: String,
    val recommended: Boolean,
    val offersQualityChoice: Boolean = false,
    val estimatedPeakMemoryBytes: Long = 0L,
    val xnnpackEligible: Boolean = false,
)
