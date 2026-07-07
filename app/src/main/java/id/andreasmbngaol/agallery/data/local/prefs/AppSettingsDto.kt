package id.andreasmbngaol.agallery.data.local.prefs

import kotlinx.serialization.Serializable

/**
 * Persisted representation stored in DataStore. Deliberately kept separate from
 * the domain [id.andreasmbngaol.agallery.domain.model.settings.AppSettings] so
 * the domain stays free of serialization annotations and insulated from storage
 * format changes.
 *
 * Every field is nullable for backward compatibility: an absent (newly added)
 * field falls back to the domain default during mapping.
 */
@Serializable
data class AppSettingsDto(
    val edgeEffectMode: String? = null,
    val componentStyle: String? = null,
    val gridColumns: Int? = null,
    val sortOrder: String? = null,
    val performanceMode: String? = null,
    val pinnedAlbumKeys: List<String>? = null,
)
