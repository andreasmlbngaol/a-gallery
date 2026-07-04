package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak penyimpanan preferensi. Implementasi (DataStore) ada di layer data.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setEdgeEffectMode(mode: EdgeEffectMode)
    suspend fun setComponentStyle(style: ComponentStyle)
    suspend fun setGridColumns(columns: Int)
    suspend fun setSortOrder(order: GallerySortOrder)
    suspend fun setPerformanceMode(mode: PerformanceMode)
    suspend fun setPinnedAlbumKeys(keys: List<String>)
}
