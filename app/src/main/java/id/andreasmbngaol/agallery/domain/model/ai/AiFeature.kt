package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A capability that an on-device AI model can provide. Every [AiModelSpec] in the
 * catalog declares the single feature it powers, and the model picker groups
 * models by feature.
 *
 * For 2.0.0 the only shipped feature is [BACKGROUND_REMOVAL]; the enum exists so
 * future features (e.g. upscaling, matting variants) can be added without
 * reshaping the model framework.
 */
enum class AiFeature {
    /** Salient-object / foreground segmentation used by the Background Remover. */
    BACKGROUND_REMOVAL,
}
