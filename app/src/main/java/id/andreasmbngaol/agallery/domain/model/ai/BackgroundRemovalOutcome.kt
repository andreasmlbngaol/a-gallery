package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of running background removal on a source image. On [Success] the
 * foreground has been composited onto transparency and written to a temporary
 * cache file ([resultPath], a PNG with an alpha channel) ready to preview; it is
 * only persisted to the gallery when the user chooses to save.
 */
sealed interface BackgroundRemovalOutcome {
    data class Success(val resultPath: String) : BackgroundRemovalOutcome

    data class Failure(val reason: Reason) : BackgroundRemovalOutcome

    /** Why background removal failed. */
    enum class Reason {
        /** No usable model is installed for the feature. */
        NO_MODEL,

        /** The source image could not be opened or decoded. */
        SOURCE_UNREADABLE,

        /** Inference or mask compositing failed. */
        FAILED,
    }
}
