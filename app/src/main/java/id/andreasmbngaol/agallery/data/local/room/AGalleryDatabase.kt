package id.andreasmbngaol.agallery.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.andreasmbngaol.agallery.data.local.room.dao.MediaDao
import id.andreasmbngaol.agallery.data.local.room.entity.AlbumCoverEntity
import id.andreasmbngaol.agallery.data.local.room.entity.FavoriteEntity
import id.andreasmbngaol.agallery.data.local.room.entity.TrashedEntity

@Database(
    entities = [FavoriteEntity::class, TrashedEntity::class, AlbumCoverEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AGalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}

/**
 * v1 -> v2: menambah tabel [TrashedEntity] ("trashed") untuk fitur Trash
 * (soft-delete 30 hari). Tabel `favorites` tidak berubah, jadi cukup CREATE
 * TABLE baru — data favorit user TETAP aman (tanpa destructive migration).
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
 * - Menambah kolom `isVideo` + `durationMs` ke tabel `trashed` supaya layar
 *   Trash bisa menampilkan badge video + durasi (baris lama default 0/false).
 * - Menambah tabel `album_cover` untuk fitur "Set as Cover" (override sampul
 *   album per user).
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
