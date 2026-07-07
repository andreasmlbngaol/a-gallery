package id.andreasmbngaol.agallery.domain.model.qr

/** Source of the center logo of a QR. */
sealed interface QrLogo {
    data object None : QrLogo
    data class BuiltIn(val logo: QrBuiltInLogo) : QrLogo
    data class Photo(val uri: String) : QrLogo
}
