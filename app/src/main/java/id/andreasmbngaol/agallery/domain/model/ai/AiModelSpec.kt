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
 * @property estimatedPeakMemoryBytes rough peak RAM a single run needs (weights
 *   plus native activations), used to advise device suitability before import;
 *   0 skips the check.
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
    val estimatedPeakMemoryBytes: Long = 0L,
)
