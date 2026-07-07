package id.andreasmbngaol.agallery.data.mapper

import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.settings.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.settings.MIN_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode

/**
 * Maps the persisted [AppSettingsDto] into the domain [AppSettings], applying
 * defaults and clamping for absent or out-of-range values and migrating legacy
 * enum names to their current equivalents.
 */
fun AppSettingsDto.toDomain(): AppSettings =
    AppSettings(
        edgeEffectMode = edgeEffectMode?.let { raw ->
            val normalized = when (raw) {
                "FROSTED" -> "BLURRY"
                "GRADIENT" -> "DARKEN"
                else -> raw
            }
            EdgeEffectMode.entries.firstOrNull { it.name == normalized }
        },
        componentStyle = componentStyle?.let { raw ->
            ComponentStyle.entries.firstOrNull { it.name == raw }
        },
        gridColumns = (gridColumns ?: DEFAULT_GRID_COLUMNS)
            .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS),
        sortOrder = sortOrder
            ?.let { raw -> GallerySortOrder.entries.firstOrNull { it.name == raw } }
            ?: GallerySortOrder.DateDesc,
        performanceMode = performanceMode
            ?.let { raw -> PerformanceMode.entries.firstOrNull { it.name == raw } }
            ?: PerformanceMode.BALANCED,
        pinnedAlbumKeys = pinnedAlbumKeys,
    )
