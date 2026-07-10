package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of running photo enhancement on a source image. On [Success] the
 * enhanced image has been written to a temporary cache file ([resultPath], a
 * PNG) ready to preview; it is only persisted to the gallery when the user
 * chooses to save. The original is never modified and its resolution is
 * preserved (Enhance restores detail, it does not enlarge).
 */
sealed interface EnhanceOutcome {
    data class Success(val resultPath: String) : EnhanceOutcome

    data class Failure(val reason: Reason) : EnhanceOutcome

    /** Why enhancement failed. */
    enum class Reason {
        /** No usable model is installed for the feature. */
        NO_MODEL,

        /** The source image could not be opened or decoded. */
        SOURCE_UNREADABLE,

        /** Inference or image assembly failed. */
        FAILED,
    }
}
