package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

class SetComponentStyleUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(style: ComponentStyle) =
        repository.setComponentStyle(style)
}
