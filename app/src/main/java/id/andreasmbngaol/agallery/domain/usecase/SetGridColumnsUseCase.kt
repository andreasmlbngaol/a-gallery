package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

class SetGridColumnsUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(columns: Int) =
        repository.setGridColumns(columns)
}
