package id.andreasmbngaol.agallery.domain.model.media

/**
 * A single media item (photo/video). Pure Kotlin — do NOT import android.*
 * here.
 *
 * @property id MediaStore id of the item.
 * @property uri Content URI of the item.
 * @property displayName File display name.
 * @property mimeType MIME type of the item.
 * @property type Whether the item is an image or a video.
 * @property dateAddedEpochSeconds Epoch seconds when the item was added.
 * @property bucketId Id of the folder (bucket) the item belongs to.
 * @property bucketName Name of the folder (bucket) the item belongs to.
 * @property durationMs Video duration in milliseconds. 0 for photos (or videos without metadata).
 * @property isFavorite Whether the item is favorited.
 */
data class MediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
    val dateAddedEpochSeconds: Long,
    val bucketId: Long,
    val bucketName: String,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
)
