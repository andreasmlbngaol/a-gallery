package id.andreasmbngaol.agallery.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-selected cover override applied through the "Set as Cover" action in the
 * viewer.
 *
 * [albumKey] is the album key (a smart key or `"bucket:<id>"`) and [mediaId] is
 * the MediaStore `_ID` of the photo or video chosen as the cover. When building
 * the album list this override is used ONLY if the item still exists and is not
 * currently in Trash; otherwise it falls back to the most recent item (the
 * default behavior).
 */
@Entity(tableName = "album_cover")
data class AlbumCoverEntity(
    @PrimaryKey val albumKey: String,
    val mediaId: Long,
)
