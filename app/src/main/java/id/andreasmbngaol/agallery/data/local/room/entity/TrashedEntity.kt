package id.andreasmbngaol.agallery.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-level (soft) trash record. A media item marked here is hidden from the
 * main gallery grid but its underlying MediaStore file is NOT deleted, so it
 * can still be restored within the retention window ([trashedAt] + 30 days).
 *
 * [isVideo] & [durationMs] direkam saat di-trash supaya layar Trash bisa
 * menampilkan badge video + durasi tanpa query MediaStore lagi.
 *
 * The permanent purge after 30 days is handled by [id.andreasmbngaol.agallery.data.local.room.dao.MediaDao.getTrashedOlderThan]
 * + [id.andreasmbngaol.agallery.data.local.room.dao.MediaDao.purgeTrashedOlderThan] (dipicu saat app start / layar Trash dibuka).
 */
@Entity(tableName = "trashed")
data class TrashedEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val trashedAt: Long,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
)
