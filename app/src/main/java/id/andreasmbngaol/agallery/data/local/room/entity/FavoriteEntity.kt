package id.andreasmbngaol.agallery.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Marks a MediaStore item as a favorite.
 *
 * [mediaId] is the MediaStore `_ID` of the favorited photo or video and
 * [createdAt] records when it was favorited (epoch milliseconds), letting the
 * Favorites album be ordered by most recently added.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    val createdAt: Long,
)
