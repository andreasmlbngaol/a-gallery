package id.andreasmbngaol.agallery.domain.model.ai

/**
 * User-selectable quality/speed trade-off for a SINGLE background-removal run,
 * chosen at inference time. This is deliberately NOT tied to the global
 * [id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode] setting
 * (which only governs gallery/album memory use): lowering the resolution changes
 * the OUTPUT quality, so it belongs to the user as an explicit per-run choice.
 *
 * Inference cost scales with the number of input pixels, so a smaller input is
 * the single biggest speed lever on CPU-bound devices. [scale] multiplies the
 * model's native input resolution. A lower scale is much faster but loses
 * fine-edge detail (hair, thin gaps).
 *
 * This only has an effect on models that accept a DYNAMIC input size (e.g. the
 * IS-Net "-dynamic" re-export). Fixed-size models transparently fall back to
 * their native resolution (see BackgroundRemovalProcessor).
 *
 * For a 1024px model: ECO -> 512, BALANCED -> 768, HIGH -> 1024.
 */
enum class RemovalQuality(val scale: Float) {
    ECO(0.5f),
    BALANCED(0.75f),
    HIGH(1.0f),
    ;

    companion object {
        /** Default keeps the model's full native resolution (best quality). */
        val DEFAULT: RemovalQuality = HIGH
    }
}
