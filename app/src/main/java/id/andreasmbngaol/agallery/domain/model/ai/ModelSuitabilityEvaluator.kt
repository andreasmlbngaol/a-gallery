package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Pure heuristic that turns a [DeviceCapability] snapshot plus a model's
 * declared peak memory and input size into a [ModelSuitability] verdict. Kept
 * Android-free so it is trivially unit-testable and reusable by any screen.
 *
 * The memory check is deliberately conservative: on-device segmentation models
 * are killed by the OS Low-Memory-Killer (an uncatchable process death, not a
 * Kotlin exception) when their native activations push the system past its
 * budget, so it is far better to warn up front than to let an import lead to a
 * force-close later.
 */
object ModelSuitabilityEvaluator {

    /**
     * Fraction of total RAM a single model run may claim before it is treated as
     * likely to be killed. The OS, other apps, and the UI need the rest.
     */
    private const val SAFE_FRACTION = 0.55

    /** Below this CPU score, heavier (non-light) models are flagged as slow. */
    private const val SLOW_CPU_SCORE = 0.7

    fun evaluate(spec: AiModelSpec, capability: DeviceCapability): ModelSuitability {
        val peak = spec.estimatedPeakMemoryBytes
        val safeBudget = (capability.totalRamBytes * SAFE_FRACTION).toLong()

        val rating = when {
            peak <= 0L -> ModelSuitability.Rating.GOOD
            capability.isLowRamMemory && spec.tier == ModelTier.HIGH_QUALITY ->
                ModelSuitability.Rating.INSUFFICIENT_MEMORY
            peak > safeBudget -> ModelSuitability.Rating.INSUFFICIENT_MEMORY
            peak > capability.availRamBytes -> ModelSuitability.Rating.SLOW
            spec.tier != ModelTier.LIGHT && capability.cpuScore in 0.0..SLOW_CPU_SCORE ->
                ModelSuitability.Rating.SLOW
            else -> ModelSuitability.Rating.GOOD
        }

        return ModelSuitability(rating)
    }
}
