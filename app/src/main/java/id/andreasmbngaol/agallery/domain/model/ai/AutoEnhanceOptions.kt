package id.andreasmbngaol.agallery.domain.model.ai

/**
 * User-chosen configuration for a single Auto Enhance run: which stages to run
 * and the per-stage blend strength (0..1) applied to each stage.
 *
 * Each stage gets its OWN strength because they behave very differently on real
 * photos:
 *  - Face Restore is the most "AI"-looking stage, so its recommended blend is
 *    kept low ([FACE_RECOMMENDED_STRENGTH]).
 *  - Enhance (denoise/deblock) is subtle, so it can run strong
 *    ([ENHANCE_RECOMMENDED_STRENGTH]).
 *  - Upscale ([UpscaleMode.AUTO]) has no native strength (Real-ESRGAN is a
 *    fixed-scale network); [upscaleStrength] is applied as an opacity blend of
 *    the AI result over a plain resize of the source, letting the user dial the
 *    aggressive super-resolution texture back toward a natural look.
 *
 * All three stages default to on: Auto Enhance is a one-tap action, and any
 * stage can be turned off for a photo that does not need it.
 */
data class AutoEnhanceOptions(
    val runFaceRestore: Boolean = true,
    val runEnhance: Boolean = true,
    val runUpscale: Boolean = true,
    val faceStrength: Float = FACE_RECOMMENDED_STRENGTH,
    val enhanceStrength: Float = ENHANCE_RECOMMENDED_STRENGTH,
    val upscaleStrength: Float = UPSCALE_RECOMMENDED_STRENGTH,
) {
    /** Whether at least one stage is enabled (there is something to run). */
    val hasAnyStage: Boolean get() = runFaceRestore || runEnhance || runUpscale

    companion object {
        /**
         * Recommended Face Restore blend. Kept low: GPEN restoration looks
         * obviously synthetic at high blends; ~30% cleans the face while still
         * reading as natural.
         */
        const val FACE_RECOMMENDED_STRENGTH = 0.3f

        /**
         * Recommended Enhance blend. Denoise/deblock is subtle, so a strong
         * blend is safe and rarely looks artificial.
         */
        const val ENHANCE_RECOMMENDED_STRENGTH = 0.8f

        /**
         * Recommended Upscale blend. Real-ESRGAN is aggressive, so the AI
         * result is mixed only partially over a plain resize; a light default
         * keeps detail without the over-sharpened, plastic texture.
         */
        const val UPSCALE_RECOMMENDED_STRENGTH = 0.4f
    }
}
