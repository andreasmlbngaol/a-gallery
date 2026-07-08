package id.andreasmbngaol.agallery.presentation.ai

import androidx.annotation.StringRes
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelTier

/**
 * Maps pure-domain AI values to localized string resources. This lives in the
 * presentation layer on purpose: the domain [id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog]
 * stays free of Android/`R` references, and all human-facing copy (tier badges,
 * per-model rationale) is resolved here.
 */
object AiModelStrings {

    /** Localized badge label for a model's quality/size tier. */
    @StringRes
    fun tierLabel(tier: ModelTier): Int = when (tier) {
        ModelTier.LIGHT -> R.string.ai_model_tier_light
        ModelTier.BALANCED -> R.string.ai_model_tier_balanced
        ModelTier.HIGH_QUALITY -> R.string.ai_model_tier_high
    }

    /**
     * A short sentence explaining when to pick this model, or 0 when there is no
     * specific rationale copy for the id.
     */
    @StringRes
    fun rationale(id: AiModelId): Int = when (id.value) {
        "isnet-general-use" -> R.string.ai_model_rationale_isnet
        "u2netp" -> R.string.ai_model_rationale_u2netp
        "birefnet-lite" -> R.string.ai_model_rationale_birefnet
        else -> 0
    }

    /** Localized label describing how well this device can run a model. */
    @StringRes
    fun suitabilityLabel(rating: ModelSuitability.Rating): Int = when (rating) {
        ModelSuitability.Rating.GOOD -> R.string.ai_model_suitability_good
        ModelSuitability.Rating.SLOW -> R.string.ai_model_suitability_slow
        ModelSuitability.Rating.INSUFFICIENT_MEMORY -> R.string.ai_model_suitability_insufficient
    }

    /** Localized coarse CPU-speed class label for a benchmark [score]. */
    @StringRes
    fun cpuClassLabel(score: Double): Int = when {
        score >= 1.3 -> R.string.ai_device_cpu_fast
        score >= 0.7 -> R.string.ai_device_cpu_medium
        else -> R.string.ai_device_cpu_slow
    }
}
