package id.andreasmbngaol.agallery.domain.model

/**
 * Satu "album" di tab Albums. Bisa album folder (Bucket) maupun album cerdas
 * (Recent, Camera, Videos, Screenshots, ScreenRecordings, Favorites).
 *
 * [key] stabil across process/rotasi -> dipakai NavKey saat buka detail album
 * & pinned/order di DataStore. [scope] = kriteria query yang dipahami
 * MediaStoreDataSource. [isSmart] memisahkan album cerdas dari folder biasa
 * (dipakai di viewer picker Move/Copy yg hanya menampilkan folder nyata).
 */
data class Album(
    val key: String,
    val scope: MediaScope,
    val name: String,
    val coverUri: String?,
    val photoCount: Int,
    val videoCount: Int,
    val isSmart: Boolean = false,
) {
    /** Total item = foto + video. */
    val itemCount: Int get() = photoCount + videoCount
}
