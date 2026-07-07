package id.andreasmbngaol.agallery.domain.model.conversion

/**
 * Target image format for the Format Converter feature (1.5.0).
 *
 * Technical notes:
 * - JPG/PNG/WEBP are encoded via `Bitmap.compress` (universal, lightweight).
 * - HEIC uses `HeifWriter` (framework), so it depends on the device's HW codec
 *   support; on failure it returns [ConversionOutcome.UnsupportedTarget].
 * - JPG and HEIC have no alpha, so transparent images are flattened onto black.
 * - PNG is lossless, so the quality slider is ignored.
 *
 * @property mimeType MIME type of the format.
 * @property extension File extension of the format.
 * @property lossy Whether the format is lossy.
 * @property supportsAlpha Whether the format supports an alpha channel.
 */
enum class ImageFormat(
    val mimeType: String,
    val extension: String,
    val lossy: Boolean,
    val supportsAlpha: Boolean,
) {
    JPG(mimeType = "image/jpeg", extension = "jpg", lossy = true, supportsAlpha = false),
    PNG(mimeType = "image/png", extension = "png", lossy = false, supportsAlpha = true),
    WEBP(mimeType = "image/webp", extension = "webp", lossy = true, supportsAlpha = true),
    HEIC(mimeType = "image/heic", extension = "heic", lossy = true, supportsAlpha = false),
    ;

    companion object {
        /** Resolves a format from the source MIME (image/jpg is treated as JPG). */
        fun fromMime(mime: String?): ImageFormat? {
            val m = mime?.lowercase()?.trim() ?: return null
            return when (m) {
                "image/jpeg", "image/jpg" -> JPG
                "image/png" -> PNG
                "image/webp" -> WEBP
                "image/heic", "image/heif" -> HEIC
                else -> null
            }
        }
    }
}
