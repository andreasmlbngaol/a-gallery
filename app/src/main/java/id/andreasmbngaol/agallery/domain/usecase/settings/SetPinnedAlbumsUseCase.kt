package id.andreasmbngaol.agallery.domain.usecase.settings

import id.andreasmbngaol.agallery.domain.repository.SettingsRepository

/**
 * Persists the order of albums pinned in the Albums tab. List order = display
 * order (left->right, top->bottom). Called when the user pins/unpins via the
 * long-press overlay or after a drag-reorder in Reorder mode.
 */
class SetPinnedAlbumsUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(keys: List<String>) {
        repository.setPinnedAlbumKeys(keys)
    }
}
