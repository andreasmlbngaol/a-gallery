package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/** Persists the chosen UI component style (Solid/Frosted/Glass). */
class SetComponentStyleUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(style: ComponentStyle) =
        repository.setComponentStyle(style)
}
