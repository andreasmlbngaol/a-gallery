package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/**
 * Persists the model used by the viewer's long-press "lift subject" feature.
 * A `null` value means Auto: the smallest installed background-removal model.
 */
class SetLiftModelUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(id: AiModelId?) =
        repository.setLiftModelId(id?.value)
}
