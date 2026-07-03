package id.andreasmbngaol.agallery.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-level (soft) trash record. A media item marked here is hidden from the
 * main gallery grid but its underlying MediaStore file is NOT deleted, so it
 * can still be restored within the retention window ([trashedAt] + 30 days).
 *
 * The permanent purge after 30 days is done by a background worker (added in a
 * later milestone); this table only stores the marker + timestamp so the
 * 30-day logic has everything it needs.
 */
@Entity(tableName = "trashed")
data class TrashedEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val trashedAt: Long,
)
