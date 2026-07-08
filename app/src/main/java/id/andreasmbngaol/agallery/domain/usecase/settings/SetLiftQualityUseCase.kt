package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/**
 * Persists the Eco/Balanced/High quality used by the viewer's long-press "lift
 * subject" gesture. Only affects models with a dynamic input (e.g. IS-Net);
 * fixed-size models always run at their native resolution.
 */
class SetLiftQualityUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(quality: RemovalQuality) =
        repository.setLiftQuality(quality)
}
