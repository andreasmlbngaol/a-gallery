package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of saving a face-restoration preview into the gallery as a new file
 * (the original is never modified). On [Success] the saved file's display name
 * is returned for a confirmation message.
 */
sealed interface FaceRestoreSaveOutcome {
    data class Success(val displayName: String) : FaceRestoreSaveOutcome

    data object Failure : FaceRestoreSaveOutcome
}
