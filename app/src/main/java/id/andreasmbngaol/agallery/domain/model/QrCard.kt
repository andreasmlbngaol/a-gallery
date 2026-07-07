package id.andreasmbngaol.agallery.domain.model

/** Gaya modul (titik) QR saat dirender. */
enum class QrDotStyle { SQUARE, DOTS }

/** Mode isi Alt text: ikut konten QR (SAME) atau diketik custom. */
enum class QrAltMode { SAME, CUSTOM }

/** Ikon logo bawaan yang bisa ditaruh di tengah QR. */
enum class QrBuiltInLogo { HEART, LOCATION }

/** Sumber logo tengah QR. */
sealed interface QrLogo {
    data object None : QrLogo
    data class BuiltIn(val logo: QrBuiltInLogo) : QrLogo
    data class Photo(val uri: String) : QrLogo
}

/** Target ekspor gambar: kartu penuh (semua section) atau QR saja (+logo). */
enum class QrExportTarget { CARD, QR_ONLY }

/**
 * Konfigurasi kartu QR. SEMUA field teks bersifat OPSIONAL: kosong = section
 * disembunyikan saat render. [content] adalah data mentah yang di-encode ke QR
 * (mis. URL / teks). [altText] default-nya mengikuti [content] (di-set di
 * ViewModel) tapi bisa diubah user.
 */
data class QrCardConfig(
    val content: String = "",
    val title: String = "",
    val subtitle: String = "",
    val altText: String = "",
    val supportingText: String = "",
    val dotStyle: QrDotStyle = QrDotStyle.DOTS,
    val logo: QrLogo = QrLogo.None,
    // Per-field styling: ukuran (sp) + warna (ARGB 0xAARRGGBB). Default =
    // tampilan sekarang. Warna disimpan Long biar domain bebas dependensi Compose.
    val titleSize: Float = 24f,
    val titleColor: Long = 0xFF0A0A0A,
    val subtitleSize: Float = 16f,
    val subtitleColor: Long = 0xFF3A3A3A,
    val altSize: Float = 13f,
    val altColor: Long = 0xFF333333,
    val supportingSize: Float = 12f,
    val supportingColor: Long = 0xFF777777,
    // Alt text: ikut konten (SAME) atau custom.
    val altMode: QrAltMode = QrAltMode.SAME,
)
