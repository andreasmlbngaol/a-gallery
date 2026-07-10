package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of running face restoration on a source image. On [Success] the photo
 * with every detected face enhanced has been written to a temporary cache file
 * ([resultPath], a PNG); it is only persisted to the gallery when the user
 * chooses to save. The original is never modified and the non-face areas of the
 * photo are left untouched.
 */
sealed interface FaceRestoreOutcome {
    data class Success(val resultPath: String) : FaceRestoreOutcome

    data class Failure(val reason: Reason) : FaceRestoreOutcome

    /** Why face restoration failed. */
    enum class Reason {
        /** No usable model is installed for the feature. */
        NO_MODEL,

        /** The source image could not be opened or decoded. */
        SOURCE_UNREADABLE,

        /** No face was found in the photo, so there was nothing to restore. */
        NO_FACES,

        /** Inference or image assembly failed. */
        FAILED,
    }
}
