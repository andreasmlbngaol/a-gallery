package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceSaveOutcome

/**
 * Contract for persisting an Auto Enhance result. The pipeline itself is
 * orchestrated by `AutoEnhanceUseCase` on top of the individual feature
 * repositories; this repository only owns the final MediaStore save (a new PNG
 * under Pictures/AGallery Auto Enhanced). All work is on-device.
 */
interface AutoEnhanceRepository {
    /**
     * Persists a previously produced preview at [resultPath] into the gallery as
     * a new PNG file derived from [sourceDisplayName] (the original is untouched).
     */
    suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): AutoEnhanceSaveOutcome
}
