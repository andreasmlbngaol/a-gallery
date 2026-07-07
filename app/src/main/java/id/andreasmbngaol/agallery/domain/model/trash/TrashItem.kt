package id.andreasmbngaol.agallery.domain.model.trash

/**
 * Domain-level record for a single item in the app-level Trash.
 *
 * The source is the Room `trashed` table -- items that have been soft-deleted
 * but whose MediaStore files are not actually removed yet, so they can still be
 * restored during the 30-day retention window.
 *
 * @property id MediaStore _ID (unique; used for restore/purge).
 * @property uri MediaStore content URI, used to load the thumbnail and build the delete request.
 * @property trashedAt Epoch milliseconds when the item was trashed; determines the remaining 30-day retention.
 * @property isVideo Stored at trash time so the Trash screen can show a video badge without re-querying MediaStore.
 * @property durationMs Stored at trash time so the Trash screen can show the duration without re-querying MediaStore.
 */
data class TrashItem(
    val id: Long,
    val uri: String,
    val trashedAt: Long,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
)
