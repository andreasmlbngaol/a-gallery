package id.andreasmbngaol.agallery.domain.model.qr

/**
 * Configuration of a QR card. ALL text fields are OPTIONAL: an empty value hides
 * that section when rendering. [content] is the raw data encoded into the QR
 * (e.g. a URL or text). [altText] defaults to [content] (set in the ViewModel)
 * but can be changed by the user.
 *
 * @property content Raw data encoded into the QR.
 * @property title Card title text.
 * @property subtitle Card subtitle text.
 * @property altText Alt text shown under the QR.
 * @property supportingText Supporting text at the bottom of the card.
 * @property dotStyle Rendering style of the QR modules.
 * @property logo Center logo source.
 * @property titleSize Title font size in sp.
 * @property titleColor Title color as ARGB (0xAARRGGBB), stored as Long so the
 *   domain stays free of Compose dependencies.
 * @property subtitleSize Subtitle font size in sp.
 * @property subtitleColor Subtitle color as ARGB (0xAARRGGBB).
 * @property altSize Alt-text font size in sp.
 * @property altColor Alt-text color as ARGB (0xAARRGGBB).
 * @property supportingSize Supporting-text font size in sp.
 * @property supportingColor Supporting-text color as ARGB (0xAARRGGBB).
 * @property altMode Whether alt text follows the content (SAME) or is custom.
 */
data class QrCardConfig(
    val content: String = "",
    val title: String = "",
    val subtitle: String = "",
    val altText: String = "",
    val supportingText: String = "",
    val dotStyle: QrDotStyle = QrDotStyle.DOTS,
    val logo: QrLogo = QrLogo.None,
    val titleSize: Float = 24f,
    val titleColor: Long = 0xFF0A0A0A,
    val subtitleSize: Float = 16f,
    val subtitleColor: Long = 0xFF3A3A3A,
    val altSize: Float = 13f,
    val altColor: Long = 0xFF333333,
    val supportingSize: Float = 12f,
    val supportingColor: Long = 0xFF777777,
    val altMode: QrAltMode = QrAltMode.SAME,
)
