package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import kotlinx.coroutines.flow.Flow

/**
 * Contract for preference storage. The implementation (DataStore) lives in the
 * data layer.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setEdgeEffectMode(mode: EdgeEffectMode)
    suspend fun setComponentStyle(style: ComponentStyle)
    suspend fun setGridColumns(columns: Int)
    suspend fun setSortOrder(order: GallerySortOrder)
    suspend fun setPerformanceMode(mode: PerformanceMode)
    suspend fun setPinnedAlbumKeys(keys: List<String>)
    suspend fun setLiftModelId(id: String?)
    suspend fun setLiftQuality(quality: RemovalQuality)
}
