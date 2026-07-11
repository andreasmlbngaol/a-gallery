package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOptions
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceProgress
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStage
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStageResult
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository
import id.andreasmbngaol.agallery.domain.repository.PhotoEnhanceRepository

/**
 * Runs the one-tap Auto Enhance pipeline on a single [sourceUri]: Enhance
 * -> Upscale -> Face Restore, in that order, using whichever stages [AutoEnhanceOptions]
 * enables. Face Restore is deliberately last so the freshly restored face is
 * never re-upscaled (which looked over-processed); the face becomes the final
 * word on the enlarged frame. Each stage reuses the existing single-feature repository, and its
 * cache-file output is fed as the input of the next stage (so later stages work
 * on the cleaned-up image, not the original). Nothing is saved to the gallery;
 * the caller saves the final preview via [SaveAutoEnhanceResultUseCase].
 *
 * The models are chosen automatically per stage (recommended installed model
 * first, with the light Real-ESRGAN x4 v3 preferred for Upscale so the one-tap
 * flow stays fast). [onProgress] streams stage start / per-tile / completion /
 * skip events so the UI can show a stepped preview and a live progress meter.
 *
 * All work is on-device; the original image is never modified.
 */
class AutoEnhanceUseCase(
    private val faceRestore: FaceRestoreRepository,
    private val photoEnhance: PhotoEnhanceRepository,
    private val imageUpscale: ImageUpscaleRepository,
    private val modelRepository: AiModelRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        options: AutoEnhanceOptions,
        onProgress: (AutoEnhanceProgress) -> Unit = {},
    ): AutoEnhanceOutcome {
        if (!options.hasAnyStage) {
            return AutoEnhanceOutcome.Failure(AutoEnhanceOutcome.Reason.NOTHING_TO_DO)
        }

        var currentUri = sourceUri
        val results = mutableListOf<AutoEnhanceStageResult>()

        // ---- Stage 1: Enhance -----------------------------------------
        if (options.runEnhance) {
            val modelId = resolveModel(AiFeature.IMAGE_ENHANCE, ENHANCE_PREFERENCE)
                ?: return AutoEnhanceOutcome.Failure(
                    AutoEnhanceOutcome.Reason.NO_MODEL, AutoEnhanceStage.ENHANCE,
                )
            onProgress(AutoEnhanceProgress.Started(AutoEnhanceStage.ENHANCE))
            val outcome = photoEnhance.enhance(currentUri, modelId, options.enhanceStrength) { c, t ->
                onProgress(AutoEnhanceProgress.Tiles(AutoEnhanceStage.ENHANCE, c, t))
            }
            when (outcome) {
                is EnhanceOutcome.Success -> {
                    results += AutoEnhanceStageResult(AutoEnhanceStage.ENHANCE, outcome.resultPath)
                    currentUri = toFileUri(outcome.resultPath)
                    onProgress(AutoEnhanceProgress.Completed(AutoEnhanceStage.ENHANCE, outcome.resultPath))
                }
                is EnhanceOutcome.Failure -> return AutoEnhanceOutcome.Failure(
                    when (outcome.reason) {
                        EnhanceOutcome.Reason.NO_MODEL -> AutoEnhanceOutcome.Reason.NO_MODEL
                        EnhanceOutcome.Reason.SOURCE_UNREADABLE -> AutoEnhanceOutcome.Reason.SOURCE_UNREADABLE
                        EnhanceOutcome.Reason.FAILED -> AutoEnhanceOutcome.Reason.FAILED
                    },
                    AutoEnhanceStage.ENHANCE,
                )
            }
        }

        // ---- Stage 2: Upscale -----------------------------------------
        if (options.runUpscale) {
            val modelId = resolveModel(AiFeature.IMAGE_UPSCALE, UPSCALE_PREFERENCE)
                ?: return AutoEnhanceOutcome.Failure(
                    AutoEnhanceOutcome.Reason.NO_MODEL, AutoEnhanceStage.UPSCALE,
                )
            onProgress(AutoEnhanceProgress.Started(AutoEnhanceStage.UPSCALE))
            val outcome = imageUpscale.upscale(currentUri, modelId, UpscaleMode.AUTO, options.upscaleStrength) { c, t ->
                onProgress(AutoEnhanceProgress.Tiles(AutoEnhanceStage.UPSCALE, c, t))
            }
            when (outcome) {
                is UpscaleOutcome.Success -> {
                    results += AutoEnhanceStageResult(AutoEnhanceStage.UPSCALE, outcome.resultPath)
                    currentUri = toFileUri(outcome.resultPath)
                    onProgress(AutoEnhanceProgress.Completed(AutoEnhanceStage.UPSCALE, outcome.resultPath))
                }
                is UpscaleOutcome.Failure -> return AutoEnhanceOutcome.Failure(
                    when (outcome.reason) {
                        UpscaleOutcome.Reason.NO_MODEL -> AutoEnhanceOutcome.Reason.NO_MODEL
                        UpscaleOutcome.Reason.SOURCE_UNREADABLE -> AutoEnhanceOutcome.Reason.SOURCE_UNREADABLE
                        UpscaleOutcome.Reason.FAILED -> AutoEnhanceOutcome.Reason.FAILED
                    },
                    AutoEnhanceStage.UPSCALE,
                )
            }
        }

        // ---- Stage 3: Face Restore (LAST) -----------------------------
        // Runs last so the restored face is never re-upscaled: Upscale
        // above already sharpened the whole frame, and re-enlarging a face
        // GPEN just cleaned makes it look over-processed. Detecting faces on
        // the enlarged frame and restoring them here makes the face the final
        // word, so the result stays natural.
        if (options.runFaceRestore) {
            val modelId = resolveModel(AiFeature.FACE_RESTORATION, FACE_PREFERENCE)
                ?: return AutoEnhanceOutcome.Failure(
                    AutoEnhanceOutcome.Reason.NO_MODEL, AutoEnhanceStage.FACE_RESTORE,
                )
            // Detection is cheap and model-independent; skip the whole restore
            // pass (and its model load) when the photo has no faces.
            val detection = faceRestore.detectFaces(currentUri)
            if (detection.faces.isEmpty()) {
                onProgress(skipNoFaces())
            } else {
                onProgress(AutoEnhanceProgress.Started(AutoEnhanceStage.FACE_RESTORE))
                val outcome = faceRestore.restore(currentUri, modelId, options.faceStrength) { c, t ->
                    onProgress(AutoEnhanceProgress.Tiles(AutoEnhanceStage.FACE_RESTORE, c, t))
                }
                when (outcome) {
                    is FaceRestoreOutcome.Success -> {
                        results += AutoEnhanceStageResult(AutoEnhanceStage.FACE_RESTORE, outcome.resultPath)
                        currentUri = toFileUri(outcome.resultPath)
                        onProgress(AutoEnhanceProgress.Completed(AutoEnhanceStage.FACE_RESTORE, outcome.resultPath))
                    }
                    is FaceRestoreOutcome.Failure -> when (outcome.reason) {
                        // A late "no faces" (detection race) is a skip, not a failure.
                        FaceRestoreOutcome.Reason.NO_FACES -> onProgress(skipNoFaces())
                        FaceRestoreOutcome.Reason.NO_MODEL -> return AutoEnhanceOutcome.Failure(
                            AutoEnhanceOutcome.Reason.NO_MODEL, AutoEnhanceStage.FACE_RESTORE,
                        )
                        FaceRestoreOutcome.Reason.SOURCE_UNREADABLE -> return AutoEnhanceOutcome.Failure(
                            AutoEnhanceOutcome.Reason.SOURCE_UNREADABLE, AutoEnhanceStage.FACE_RESTORE,
                        )
                        FaceRestoreOutcome.Reason.FAILED -> return AutoEnhanceOutcome.Failure(
                            AutoEnhanceOutcome.Reason.FAILED, AutoEnhanceStage.FACE_RESTORE,
                        )
                    }
                }
            }
        }

        val finalPath = results.lastOrNull()?.resultPath
            ?: return AutoEnhanceOutcome.Failure(AutoEnhanceOutcome.Reason.NOTHING_PRODUCED)
        return AutoEnhanceOutcome.Success(finalPath, results.toList())
    }

    /**
     * Picks the model to run for [feature]: the first installed id in
     * [preference], else any installed model for the feature (recommended
     * first), else null when none is installed.
     */
    private suspend fun resolveModel(
        feature: AiFeature,
        preference: List<AiModelId>,
    ): AiModelId? {
        for (id in preference) {
            if (modelRepository.installed(id) != null) return id
        }
        return ModelCatalog.forFeature(feature)
            .firstOrNull { modelRepository.installed(it.id) != null }
            ?.id
    }

    private fun skipNoFaces() = AutoEnhanceProgress.Skipped(
        AutoEnhanceStage.FACE_RESTORE,
        AutoEnhanceProgress.SkipReason.NO_FACES,
    )

    /** Wraps an absolute cache-file path as a file:// uri for the next stage. */
    private fun toFileUri(path: String): String =
        if (path.startsWith("file://")) path else "file://$path"

    private companion object {
        // Face Restore: prefer the sharper 512 model, fall back to the light 256.
        val FACE_PREFERENCE = listOf(
            ModelCatalog.GPEN_BFR_512.id,
            ModelCatalog.GPEN_BFR_256.id,
        )

        // Enhance: prefer the recommended sharp SCUNet, fall back to the clean one.
        val ENHANCE_PREFERENCE = listOf(
            ModelCatalog.SCUNET_GAN.id,
            ModelCatalog.SCUNET_PSNR.id,
        )

        // Upscale: prefer the light x4 v3 (fast, low memory) for the one-tap flow,
        // fall back to the heavier x4plus only if v3 is not installed.
        val UPSCALE_PREFERENCE = listOf(
            ModelCatalog.REAL_ESRGAN_GENERAL_X4V3.id,
            ModelCatalog.REAL_ESRGAN_X4PLUS.id,
        )
    }
}
