package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/** Persists the chosen performance mode (RAM vs. scroll-smoothness trade-off). */
class SetPerformanceModeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: PerformanceMode) =
        repository.setPerformanceMode(mode)
}
