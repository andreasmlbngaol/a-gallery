package id.andreasmbngaol.agallery.domain.model.settings

import kotlinx.serialization.Serializable

/**
 * Sort order for the media list in the gallery.
 *
 * - [DateDesc]: newest first (default) — `DATE_ADDED DESC`.
 * - [DateAsc]: oldest first — `DATE_ADDED ASC`.
 *
 * Selected by the user via the sort button in the bottom floating bar.
 */
@Serializable
enum class GallerySortOrder { DateDesc, DateAsc }
