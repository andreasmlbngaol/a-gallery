package id.andreasmbngaol.agallery.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AGalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
