package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

class SetEdgeEffectModeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: EdgeEffectMode) =
        repository.setEdgeEffectMode(mode)
}
