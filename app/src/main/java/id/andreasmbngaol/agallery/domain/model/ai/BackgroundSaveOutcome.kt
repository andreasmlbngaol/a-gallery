package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of saving a background-removal preview into the gallery as a new file
 * (the original is never modified). On [Success] the saved file's display name
 * is returned for a confirmation message.
 */
sealed interface BackgroundSaveOutcome {
    data class Success(val displayName: String) : BackgroundSaveOutcome

    data object Failure : BackgroundSaveOutcome
}
