package id.andreasmbngaol.agallery.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import id.andreasmbngaol.agallery.data.local.room.entity.AlbumCoverEntity
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT mediaId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Upsert
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: Long)

    // --- Trash (soft-delete, retensi 30 hari) ---

    /** ID media yg sedang di Trash; dipakai repo utk menyaring grid & viewer. */
    @Query("SELECT mediaId FROM trashed")
    fun observeTrashedIds(): Flow<List<Long>>

    /** Isi Trash lengkap (terbaru dulu) — untuk layar Trash. */
    @Query("SELECT * FROM trashed ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<TrashedEntity>>

    @Upsert
    suspend fun addTrashed(trashed: TrashedEntity)

    @Query("DELETE FROM trashed WHERE mediaId = :mediaId")
    suspend fun removeTrashed(mediaId: Long)

    /** Marker Trash yg lebih tua dari [threshold] (epoch ms) — utk purge 30 hari. */
    @Query("SELECT * FROM trashed WHERE trashedAt < :threshold")
    suspend fun getTrashedOlderThan(threshold: Long): List<TrashedEntity>

    /** Hapus marker Trash yg lebih tua dari [threshold] (epoch ms) → purge 30 hari. */
    @Query("DELETE FROM trashed WHERE trashedAt < :threshold")
    suspend fun purgeTrashedOlderThan(threshold: Long)

    // --- Album cover override ("Set as Cover") ---

    @Query("SELECT * FROM album_cover")
    fun observeAlbumCovers(): Flow<List<AlbumCoverEntity>>

    @Upsert
    suspend fun setAlbumCover(cover: AlbumCoverEntity)

    @Query("DELETE FROM album_cover WHERE albumKey = :albumKey")
    suspend fun removeAlbumCover(albumKey: String)
}
