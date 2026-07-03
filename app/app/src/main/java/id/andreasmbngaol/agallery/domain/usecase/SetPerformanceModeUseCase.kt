package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

class SetPerformanceModeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: PerformanceMode) =
        repository.setPerformanceMode(mode)
}
