package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/** Persists the chosen screen-edge effect mode. */
class SetEdgeEffectModeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: EdgeEffectMode) =
        repository.setEdgeEffectMode(mode)
}
