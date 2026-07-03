package id.andreasmbngaol.agallery.domain.model

import kotlinx.serialization.Serializable

/**
 * Urutan sortir list media di galeri.
 *
 * - [DateDesc]: paling baru dulu (default) — `DATE_ADDED DESC`.
 * - [DateAsc] : paling lama dulu — `DATE_ADDED ASC`.
 *
 * Dipilih user lewat tombol sort di floating bar bawah.
 */
@Serializable
enum class GallerySortOrder { DateDesc, DateAsc }
