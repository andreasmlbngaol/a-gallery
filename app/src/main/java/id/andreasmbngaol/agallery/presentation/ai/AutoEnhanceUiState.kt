package id.andreasmbngaol.agallery.presentation.ai

import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOptions
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStage
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStageResult
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * UI state for the Auto Enhance screen: the one-tap pipeline that chains Face
 * Restore -> Enhance -> Upscale on a single image.
 *
 * Because the pipeline runs three separate models, the screen first gates on
 * ALL three being installed ([hasAllModels]); the per-feature flags let the gate
 * spell out exactly which model is missing. During a run it reports the current
 * [currentStage] plus TILE progress (like the single features) and keeps every
 * finished stage's preview in [stageResults] so the result is shown step by step.
 *
 * @property sourceUri the image being processed.
 * @property sourceDisplayName the original file name, used to derive the saved name.
 * @property checkingModels whether the installed-model check is still running.
 * @property hasFaceModel whether a Face Restore model is installed.
 * @property hasEnhanceModel whether an Enhance model is installed.
 * @property hasUpscaleModel whether an Upscale model is installed.
 * @property hasAllModels whether all three required models are installed.
 * @property runFaceRestore whether the Face Restore stage is enabled.
 * @property runEnhance whether the Enhance stage is enabled.
 * @property runUpscale whether the Upscale stage is enabled.
 * @property faceStrength blend strength (0..1) for the Face Restore stage.
 * @property enhanceStrength blend strength (0..1) for the Enhance stage.
 * @property upscaleStrength blend strength (0..1) for the Upscale stage (AI vs plain resize).
 * @property processing whether the pipeline is currently running.
 * @property saving whether the final result is being saved to the gallery.
 * @property currentStage the stage currently running, or null between/outside stages.
 * @property plannedStages the enabled stages for the active run, in order.
 * @property stageResults each finished stage's preview, in order.
 * @property faceSkipped whether Face Restore was skipped (no faces detected).
 * @property finalPath the final preview PNG path once produced, else null.
 * @property componentStyleChosen chosen component style, or null while loading.
 * @property edgeEffectMode chosen edge-effect mode, or null while loading.
 * @property processingCompletedTiles tiles finished so far in the current stage.
 * @property processingTotalTiles total tiles the current stage is split into.
 * @property processingElapsedSeconds seconds elapsed in the current run, shown live.
 * @property processingUsedMemoryBytes process memory (PSS) sampled during the run.
 * @property processingEtaSeconds estimated seconds remaining in the current stage, or -1 while estimating.
 */
data class AutoEnhanceUiState(
    val sourceUri: String = "",
    val sourceDisplayName: String = "",
    val checkingModels: Boolean = true,
    val hasFaceModel: Boolean = false,
    val hasEnhanceModel: Boolean = false,
    val hasUpscaleModel: Boolean = false,
    val hasAllModels: Boolean = false,
    val runFaceRestore: Boolean = true,
    val runEnhance: Boolean = true,
    val runUpscale: Boolean = true,
    val faceStrength: Float = AutoEnhanceOptions.FACE_RECOMMENDED_STRENGTH,
    val enhanceStrength: Float = AutoEnhanceOptions.ENHANCE_RECOMMENDED_STRENGTH,
    val upscaleStrength: Float = AutoEnhanceOptions.UPSCALE_RECOMMENDED_STRENGTH,
    val processing: Boolean = false,
    val saving: Boolean = false,
    val currentStage: AutoEnhanceStage? = null,
    val plannedStages: List<AutoEnhanceStage> = emptyList(),
    val stageResults: List<AutoEnhanceStageResult> = emptyList(),
    val faceSkipped: Boolean = false,
    val finalPath: String? = null,
    val componentStyleChosen: ComponentStyle? = null,
    val edgeEffectMode: EdgeEffectMode? = null,
    val processingCompletedTiles: Int = 0,
    val processingTotalTiles: Int = 0,
    val processingElapsedSeconds: Int = 0,
    val processingUsedMemoryBytes: Long = 0L,
    val processingEtaSeconds: Int = -1,
) {
    /** Whether at least one stage is enabled (the run button should be active). */
    val hasAnyStage: Boolean get() = runFaceRestore || runEnhance || runUpscale

    /** 1-based position of the current stage within [plannedStages] (0 if none). */
    val currentStageNumber: Int
        get() = currentStage?.let { plannedStages.indexOf(it) + 1 } ?: 0
}
