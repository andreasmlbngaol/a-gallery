package id.andreasmbngaol.agallery.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import id.andreasmbngaol.agallery.data.local.room.entity.AlbumCoverEntity
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the app's local state: favorites, soft-delete Trash markers, and
 * per-album cover overrides.
 */
@Dao
interface MediaDao {
    @Query("SELECT mediaId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Upsert
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: Long)

    /** IDs of media currently in Trash; used by the repository to filter the grid & viewer. */
    @Query("SELECT mediaId FROM trashed")
    fun observeTrashedIds(): Flow<List<Long>>

    /** Full Trash contents (newest first) -- for the Trash screen. */
    @Query("SELECT * FROM trashed ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<TrashedEntity>>

    @Upsert
    suspend fun addTrashed(trashed: TrashedEntity)

    @Query("DELETE FROM trashed WHERE mediaId = :mediaId")
    suspend fun removeTrashed(mediaId: Long)

    /** Trash markers older than [threshold] (epoch ms) -- for the 30-day purge. */
    @Query("SELECT * FROM trashed WHERE trashedAt < :threshold")
    suspend fun getTrashedOlderThan(threshold: Long): List<TrashedEntity>

    /** Deletes Trash markers older than [threshold] (epoch ms) -> the 30-day purge. */
    @Query("DELETE FROM trashed WHERE trashedAt < :threshold")
    suspend fun purgeTrashedOlderThan(threshold: Long)

    @Query("SELECT * FROM album_cover")
    fun observeAlbumCovers(): Flow<List<AlbumCoverEntity>>

    @Upsert
    suspend fun setAlbumCover(cover: AlbumCoverEntity)

    @Query("DELETE FROM album_cover WHERE albumKey = :albumKey")
    suspend fun removeAlbumCover(albumKey: String)
}
