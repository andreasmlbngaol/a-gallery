package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of importing a model file. On [Success] the copied, verified model is
 * returned; on [Failure] a [Reason] explains why the import was rejected so the
 * UI can show an accurate message (and leave no partial file behind).
 */
sealed interface ImportOutcome {
    data class Success(val model: InstalledModel) : ImportOutcome

    data class Failure(val reason: Reason) : ImportOutcome

    /** Why an import failed. */
    enum class Reason {
        /** Reading the picked file or writing it to app storage failed. */
        COPY_FAILED,

        /** The file size did not match the catalog's expected size. */
        SIZE_MISMATCH,

        /** The SHA-256 checksum did not match the catalog's expected value. */
        CHECKSUM_MISMATCH,

        /** The file is not a loadable ONNX model. */
        INVALID_MODEL,

        /** The model loaded but a one-shot inference probe failed. */
        INFERENCE_FAILED,
    }
}
