package id.andreasmbngaol.agallery.data.local.prefs

import kotlinx.serialization.Serializable

/**
 * Representasi tersimpan di DataStore. Sengaja dipisah dari domain
 * [id.andreasmbngaol.agallery.domain.model.AppSettings] supaya domain bersih
 * dari anotasi serialization & bebas dari perubahan format storage.
 *
 * Semua field nullable = toleran versi lama (field baru absen -> pakai default
 * domain saat mapping).
 */
@Serializable
data class AppSettingsDto(
    val edgeEffectMode: String? = null,
    val gridColumns: Int? = null,
    val sortOrder: String? = null,
    val performanceMode: String? = null,
)
