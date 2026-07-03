package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

class SetSortOrderUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(order: GallerySortOrder) =
        repository.setSortOrder(order)
}
