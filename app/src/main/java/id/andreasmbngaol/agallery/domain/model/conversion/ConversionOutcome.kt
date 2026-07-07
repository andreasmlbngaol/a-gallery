package id.andreasmbngaol.agallery.domain.model.conversion

/**
 * Result of a format conversion attempt. A conversion always creates a new file
 * (a different format means a new file); the decision to delete the original
 * (moving it to Trash) is handled by an upper layer, not here.
 */
sealed interface ConversionOutcome {
    /** Success. [displayName] is the name of the resulting file. */
    data class Success(val displayName: String) : ConversionOutcome

    /** The source could not be decoded (corrupt or unsupported for reading). */
    data object UnsupportedSource : ConversionOutcome

    /** The target is unsupported on this device (e.g. HEIC without a HW encoder). */
    data object UnsupportedTarget : ConversionOutcome

    /** I/O or other failure. */
    data object Failed : ConversionOutcome
}
