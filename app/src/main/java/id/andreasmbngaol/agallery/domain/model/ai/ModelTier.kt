package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Rough quality / size positioning of a model, rendered as a badge in the model
 * list so the user can weigh the trade-off between speed & size versus output
 * quality before importing.
 */
enum class ModelTier {
    /** Smallest & fastest, lowest fidelity (e.g. U²-Net Lite, ~5 MB). */
    LIGHT,

    /** Balanced size vs. quality; the recommended default (e.g. IS-Net). */
    BALANCED,

    /** Highest quality, largest & slowest (heavy transformer-based models). */
    HIGH_QUALITY,
}
