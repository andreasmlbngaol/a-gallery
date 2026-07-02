package id.andreasmbngaol.agallery.data.repository

import androidx.datastore.core.DataStore
import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.data.mapper.toDomain
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.MIN_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
}
