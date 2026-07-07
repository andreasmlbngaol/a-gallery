package id.andreasmbngaol.agallery.domain.model

/**
 * Format gambar tujuan untuk fitur Format Converter (1.5.0).
 *
 * Catatan teknis:
 * - JPG/PNG/WEBP di-encode via `Bitmap.compress` (universal, ringan).
 * - HEIC pakai `HeifWriter` (framework) -> tergantung dukungan codec HW device;
 *   kalau gagal, kembalikan [ConversionOutcome.UnsupportedTarget].
 * - JPG & HEIC TIDAK punya alpha -> gambar transparan di-flatten ke hitam.
 * - PNG lossless -> slider kualitas diabaikan.
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
        /** Cari format dari MIME sumber (image/jpg diperlakukan sbg JPG). */
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

/**
 * Hasil percobaan konversi format. Konversi SELALU membuat file baru (format
 * beda = file baru); keputusan "hapus asli" ditangani di layer atas (pindah ke
 * Trash), bukan di sini.
 */
sealed interface ConversionOutcome {
    /** Berhasil. [displayName] = nama file hasil. */
    data class Success(val displayName: String) : ConversionOutcome

    /** Sumber tak bisa di-decode (format rusak / tak didukung baca). */
    data object UnsupportedSource : ConversionOutcome

    /** Target tak didukung device (mis. HEIC di HP tanpa encoder HW). */
    data object UnsupportedTarget : ConversionOutcome

    /** Gagal I/O atau lainnya. */
    data object Failed : ConversionOutcome
}
