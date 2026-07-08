package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Verdict on whether the current device can run a given model, derived from a
 * [DeviceCapability] snapshot and the model's declared memory / compute demands.
 * Shown on the models screen next to each model so the user can judge the
 * trade-off before downloading and importing the weights.
 *
 * @property rating the coarse suitability bucket.
 */
data class ModelSuitability(
    val rating: Rating,
) {
    enum class Rating {
        /** Comfortably within the device's memory and reasonably fast. */
        GOOD,

        /** Runs, but likely slow and/or memory-tight; usable with patience. */
        SLOW,

        /** Likely to exhaust memory and be killed by the OS; not advised. */
        INSUFFICIENT_MEMORY,
    }
}
