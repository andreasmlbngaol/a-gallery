package id.andreasmbngaol.agallery.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT mediaId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Upsert
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: Long)
}
