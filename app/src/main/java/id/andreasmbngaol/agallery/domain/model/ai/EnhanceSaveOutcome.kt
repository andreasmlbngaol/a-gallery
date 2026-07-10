package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of saving an enhanced preview into the gallery as a new file (the
 * original is never modified). On [Success] the saved file's display name is
 * returned for a confirmation message.
 */
sealed interface EnhanceSaveOutcome {
    data class Success(val displayName: String) : EnhanceSaveOutcome

    data object Failure : EnhanceSaveOutcome
}
