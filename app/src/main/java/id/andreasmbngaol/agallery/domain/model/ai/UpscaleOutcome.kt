package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of running image upscaling on a source image. On [Success] the
 * enlarged image has been written to a temporary cache file ([resultPath], a
 * PNG) ready to preview; it is only persisted to the gallery when the user
 * chooses to save. The original is never modified.
 */
sealed interface UpscaleOutcome {
    data class Success(val resultPath: String) : UpscaleOutcome

    data class Failure(val reason: Reason) : UpscaleOutcome

    /** Why upscaling failed. */
    enum class Reason {
        /** No usable model is installed for the feature. */
        NO_MODEL,

        /** The source image could not be opened or decoded. */
        SOURCE_UNREADABLE,

        /** Inference or image assembly failed. */
        FAILED,
    }
}
