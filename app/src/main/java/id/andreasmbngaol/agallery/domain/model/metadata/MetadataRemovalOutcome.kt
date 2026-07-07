package id.andreasmbngaol.agallery.domain.model.metadata

import android.content.IntentSender

/**
 * Result of a metadata-removal attempt. Mirrors the rename/delete pattern that
 * uses an IntentSender for write consent on non-owned files (API 30+ /
 * RecoverableSecurityException).
 */
sealed interface MetadataRemovalOutcome {
    /** Success. [savedAsCopy] is true when the result was saved as a clean copy. */
    data class Success(val savedAsCopy: Boolean) : MetadataRemovalOutcome

    /** Requires user consent first (file not owned by the app). Retry after approval. */
    data class NeedsConsent(val intentSender: IntentSender) : MetadataRemovalOutcome

    /** Format unsupported for lossless stripping (e.g. HEIC/HEIF, video). */
    data object UnsupportedFormat : MetadataRemovalOutcome

    /** Failed due to an I/O or other error. */
    data object Failed : MetadataRemovalOutcome
}
