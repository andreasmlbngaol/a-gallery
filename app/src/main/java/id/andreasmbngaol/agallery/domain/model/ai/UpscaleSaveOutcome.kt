package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of saving an upscaled preview into the gallery as a new file (the
 * original is never modified). On [Success] the saved file's display name is
 * returned for a confirmation message.
 */
sealed interface UpscaleSaveOutcome {
    data class Success(val displayName: String) : UpscaleSaveOutcome

    data object Failure : UpscaleSaveOutcome
}
