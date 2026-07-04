package id.andreasmbngaol.agallery.domain.model

/**
 * Domain-level record untuk satu item di app-level Trash.
 *
 * Sumbernya adalah tabel Room `trashed` -- item yg sudah di-soft-delete
 * TAPI file MediaStore-nya belum benar-benar dihapus, sehingga masih bisa
 * di-Restore selama retensi 30 hari.
 *
 * [id] = MediaStore _ID (unik, dipakai untuk restore/purge).
 * [uri] = content-uri MediaStore utk load thumbnail & bangun delete-request.
 * [trashedAt] = epoch-ms saat di-trash; menentukan sisa retensi 30 hari.
 */
data class TrashItem(
    val id: Long,
    val uri: String,
    val trashedAt: Long,
)
