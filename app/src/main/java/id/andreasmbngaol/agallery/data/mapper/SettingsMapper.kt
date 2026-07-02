package id.andreasmbngaol.agallery.data.mapper

import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.DEFAULT_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.MIN_GRID_COLUMNS

fun AppSettingsDto.toDomain(): AppSettings =
    AppSettings(
        edgeEffectMode = edgeEffectMode?.let { raw ->
            EdgeEffectMode.entries.firstOrNull { it.name == raw }
        },
        gridColumns = (gridColumns ?: DEFAULT_GRID_COLUMNS)
            .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS),
        sortOrder = sortOrder
            ?.let { raw -> GallerySortOrder.entries.firstOrNull { it.name == raw } }
            ?: GallerySortOrder.DateDesc,
    )

fun AppSettings.toDto(): AppSettingsDto =
    AppSettingsDto(
        edgeEffectMode = edgeEffectMode?.name,
        gridColumns = gridColumns,
        sortOrder = sortOrder.name,
    )
