package id.andreasmbngaol.agallery.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.AlbumCoverEntity
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity

/**
 * Room database backing the app's local state: favorites, the soft-delete Trash
 * records, and per-album cover overrides.
 *
 * Schema changes are applied through [MIGRATION_1_2] and [MIGRATION_2_3] so user
 * data survives upgrades without a destructive migration.
 */
@Database(
    entities = [FavoriteEntity::class, TrashedEntity::class, AlbumCoverEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AGalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}

/**
 * v1 -> v2: adds the [TrashedEntity] ("trashed") table for the Trash feature
 * (30-day soft delete). The `favorites` table is unchanged, so only a new CREATE
 * TABLE is required -- existing user favorites stay intact (no destructive
 * migration).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trashed` (" +
                "`mediaId` INTEGER NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`trashedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`))",
        )
    }
}

/**
 * v2 -> v3:
 * - Adds the `isVideo` and `durationMs` columns to the `trashed` table so the
 *   Trash screen can show the video badge and duration (existing rows default to
 *   0/false).
 * - Adds the `album_cover` table for the "Set as Cover" feature (per-user album
 *   cover override).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trashed` ADD COLUMN `isVideo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `trashed` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_cover` (" +
                "`albumKey` TEXT NOT NULL, " +
                "`mediaId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`albumKey`))",
        )
    }
}
