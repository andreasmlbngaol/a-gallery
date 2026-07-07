package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/** Persists the chosen gallery grid column count. */
class SetGridColumnsUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(columns: Int) =
        repository.setGridColumns(columns)
}
