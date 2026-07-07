package id.andreasmbngaol.agallery.domain.model.media

/**
 * Extra metadata for a media item that is intentionally NOT loaded into
 * [MediaItem] so the grid query stays lightweight. Fetched on demand when the
 * user opens the detail panel in the viewer (swipe up). Applies to both photos
 * and videos.
 *
 * Core fields (size, dimensions, path) are always present. Optional fields
 * (camera/EXIF, location, video technicals) are null when the media simply does
 * not store them (e.g. a screenshot or a download), so the UI hides null rows
 * and only shows "-" for empty core fields.
 *
 * Locale-independent technical values (aperture, shutter, bitrate, etc.) are
 * already formatted in the data layer. Values needing localization (flash) or
 * locale formatting (date, coordinates) are left raw to be formatted in the UI.
 *
 * Pure Kotlin — do NOT import android.* here.
 *
 * @property sizeBytes File size in bytes. 0 when unknown.
 * @property width Width in pixels. 0 when unknown.
 * @property height Height in pixels. 0 when unknown.
 * @property relativePath Relative folder of the file (e.g. "DCIM/Camera/"). Empty when absent.
 * @property cameraMake Camera manufacturer, e.g. "Google".
 * @property cameraModel Camera/phone model, e.g. "Pixel 8".
 * @property aperture Formatted aperture, e.g. "f/1.8".
 * @property shutterSpeed Formatted shutter speed, e.g. "1/125 s".
 * @property iso ISO value, e.g. "100".
 * @property focalLength Formatted focal length, e.g. "24 mm".
 * @property flashFired Whether the flash fired. null when not recorded.
 * @property dateTakenEpochSeconds Epoch seconds when the media was captured
 *   (distinct from the date it was added).
 * @property latitude GPS latitude, or null when absent.
 * @property longitude GPS longitude, or null when absent.
 * @property frameRate Formatted frame rate, e.g. "30 fps".
 * @property bitrate Formatted bitrate, e.g. "12.0 Mbps".
 * @property videoCodec Human-readable video codec, e.g. "H.264".
 * @property audioCodec Human-readable audio codec, e.g. "AAC".
 */
data class MediaDetails(
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,

    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val flashFired: Boolean? = null,

    val dateTakenEpochSeconds: Long? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    val frameRate: String? = null,
    val bitrate: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)
