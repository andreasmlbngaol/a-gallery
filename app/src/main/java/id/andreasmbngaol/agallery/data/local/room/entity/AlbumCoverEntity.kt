package id.andreasmbngaol.agallery.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Override cover album yang dipilih user lewat aksi "Set as Cover" di viewer.
 *
 * [albumKey] = kunci album (smart atau "bucket:<id>"), [mediaId] = MediaStore
 * _ID foto/video yang dipilih jadi sampul. Saat membangun daftar album, cover
 * ini dipakai HANYA jika item-nya masih ada & tidak sedang di Trash; kalau
 * tidak, otomatis fallback ke item terbaru (perilaku default).
 */
@Entity(tableName = "album_cover")
data class AlbumCoverEntity(
    @PrimaryKey val albumKey: String,
    val mediaId: Long,
)
