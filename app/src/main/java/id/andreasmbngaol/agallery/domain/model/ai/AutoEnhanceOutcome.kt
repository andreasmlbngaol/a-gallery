package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Result of a full Auto Enhance run. On [Success], [finalPath] is the last
 * stage's preview PNG (ready to save) and [stageResults] holds every completed
 * stage's output in order for the step-by-step preview. Nothing is written to
 * the gallery until the user saves, and the original is never modified.
 */
sealed interface AutoEnhanceOutcome {
    data class Success(
        val finalPath: String,
        val stageResults: List<AutoEnhanceStageResult>,
    ) : AutoEnhanceOutcome

    /** The run failed at [stage] (null when it failed before any stage started). */
    data class Failure(
        val reason: Reason,
        val stage: AutoEnhanceStage? = null,
    ) : AutoEnhanceOutcome

    /** Why an Auto Enhance run failed. */
    enum class Reason {
        /** A required model for one of the enabled stages is not installed. */
        NO_MODEL,

        /** The source image could not be opened or decoded. */
        SOURCE_UNREADABLE,

        /** Inference or image assembly failed in one of the stages. */
        FAILED,

        /** No stage was enabled, so there was nothing to run. */
        NOTHING_TO_DO,

        /** Every enabled stage was skipped, so no result was produced. */
        NOTHING_PRODUCED,
    }
}
