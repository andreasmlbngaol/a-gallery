package id.andreasmbngaol.agallery.domain.model.ai

/**
 * The preview PNG produced by one completed Auto Enhance [stage]. The pipeline
 * keeps every stage's output (not just the final one) so the UI can show the
 * progression step by step. [resultPath] is a cache file; it is only persisted
 * to the gallery when the user saves.
 */
data class AutoEnhanceStageResult(
    val stage: AutoEnhanceStage,
    val resultPath: String,
)
