package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A capability that an on-device AI model can provide. Every [AiModelSpec] in the
 * catalog declares the single feature it powers, and the model picker groups
 * models by feature.
 *
 * Features are intentionally MODULAR (one enum entry per capability) even though
 * the UI may later wrap several of them behind a single "Auto Enhance" action:
 * keeping them separate lets each declare its own models, I/O spec, and tiering.
 */
enum class AiFeature {
    /** Salient-object / foreground segmentation used by the Background Remover. */
    BACKGROUND_REMOVAL,

    /** Super-resolution (4x) upscaling used by the Image Enhancer (2.2.0). */
    IMAGE_UPSCALE,
}
