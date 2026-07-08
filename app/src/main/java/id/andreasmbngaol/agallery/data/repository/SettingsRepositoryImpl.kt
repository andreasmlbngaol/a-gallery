package id.andreasmbngaol.agallery.data.repository

import androidx.datastore.core.DataStore
import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.data.mapper.toDomain
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.settings.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import id.andreasmbngaol.agallery.domain.model.settings.MIN_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by a typed DataStore of [AppSettingsDto].
 *
 * Reads expose the persisted settings as a mapped domain [AppSettings] stream,
 * and each setter updates a single field, storing enums by name.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<AppSettingsDto>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data.map { it.toDomain() }

    override suspend fun setEdgeEffectMode(mode: EdgeEffectMode) {
        dataStore.updateData { current ->
            current.copy(edgeEffectMode = mode.name)
        }
    }

    override suspend fun setComponentStyle(style: ComponentStyle) {
        dataStore.updateData { current ->
            current.copy(componentStyle = style.name)
        }
    }

    override suspend fun setGridColumns(columns: Int) {
        val safe = columns.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        dataStore.updateData { current ->
            current.copy(gridColumns = safe)
        }
    }

    override suspend fun setSortOrder(order: GallerySortOrder) {
        dataStore.updateData { current ->
            current.copy(sortOrder = order.name)
        }
    }

    override suspend fun setPerformanceMode(mode: PerformanceMode) {
        dataStore.updateData { current ->
            current.copy(performanceMode = mode.name)
        }
    }

    override suspend fun setPinnedAlbumKeys(keys: List<String>) {
        dataStore.updateData { current ->
            current.copy(pinnedAlbumKeys = keys)
        }
    }

    override suspend fun setLiftModelId(id: String?) {
        dataStore.updateData { current ->
            current.copy(liftModelId = id)
        }
    }

    override suspend fun setLiftQuality(quality: RemovalQuality) {
        dataStore.updateData { current ->
            current.copy(liftQuality = quality.name)
        }
    }
}
