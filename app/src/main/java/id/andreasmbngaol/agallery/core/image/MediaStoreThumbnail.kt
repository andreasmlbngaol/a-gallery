package id.andreasmbngaol.agallery.core.image

/**
 * Lightweight model handed to Coil as the `data` for a grid thumbnail request.
 *
 * Using a dedicated type (instead of a plain `String` or `Uri`) routes grid
 * requests to [MediaStoreThumbnailFetcher] rather than Coil's default fetcher,
 * which would open and decode the full-resolution photo.
 *
 * @property uri the `content://` uri of the item (photo or video).
 * @property isVideo whether the item is a video; `loadThumbnail` extracts a
 *   single frame automatically, so no separate video-frame decoder is needed
 *   for the grid.
 */
data class MediaStoreThumbnail(
    val uri: String,
    val isVideo: Boolean,
)
