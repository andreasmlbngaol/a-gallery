package id.andreasmbngaol.agallery.data.mapper

import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.MIN_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.PerformanceMode

fun AppSettingsDto.toDomain(): AppSettings =
    AppSettings(
        edgeEffectMode = edgeEffectMode?.let { raw ->
            // Migrasi nama lama -> baru (rilis <= 0.3.9 menyimpan FROSTED/GRADIENT).
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

//fun AppSettings.toDto(): AppSettingsDto =
//    AppSettingsDto(
//        edgeEffectMode = edgeEffectMode?.name,
//        componentStyle = componentStyle?.name,
//        gridColumns = gridColumns,
//        sortOrder = sortOrder.name,
//        performanceMode = performanceMode.name,
//        pinnedAlbumKeys = pinnedAlbumKeys,
//    )
