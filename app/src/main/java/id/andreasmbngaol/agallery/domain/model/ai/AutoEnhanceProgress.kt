package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A progress event emitted by [AutoEnhanceUseCase] as the pipeline advances, so
 * the UI can show which [stage] is running, its per-tile progress, and each
 * stage's result as it lands.
 */
sealed interface AutoEnhanceProgress {
    /** The stage this event refers to. */
    val stage: AutoEnhanceStage

    /** The stage has begun (its model is about to load / run). */
    data class Started(override val stage: AutoEnhanceStage) : AutoEnhanceProgress

    /** [completed] of [total] tiles finished for the current stage. */
    data class Tiles(
        override val stage: AutoEnhanceStage,
        val completed: Int,
        val total: Int,
    ) : AutoEnhanceProgress

    /** The stage finished; [resultPath] is its preview PNG (fed to the next stage). */
    data class Completed(
        override val stage: AutoEnhanceStage,
        val resultPath: String,
    ) : AutoEnhanceProgress

    /** The stage was skipped (e.g. Face Restore when the photo has no faces). */
    data class Skipped(
        override val stage: AutoEnhanceStage,
        val reason: SkipReason,
    ) : AutoEnhanceProgress

    /** Why a stage was skipped. */
    enum class SkipReason {
        /** No face was detected, so there was nothing for Face Restore to do. */
        NO_FACES,
    }
}
