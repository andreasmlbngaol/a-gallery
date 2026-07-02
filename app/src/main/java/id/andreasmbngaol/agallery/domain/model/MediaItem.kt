package id.andreasmbngaol.agallery.domain.model

/**
 * Representasi 1 item media (foto/video). Pure Kotlin.
 * JANGAN import android.* di sini.
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
    /** Durasi video dalam milidetik. 0 untuk foto (atau video tanpa metadata). */
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
)
