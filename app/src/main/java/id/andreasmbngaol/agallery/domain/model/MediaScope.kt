package id.andreasmbngaol.agallery.domain.model

/**
 * Cakupan (scope) media yang ditampilkan di grid / viewer.
 *
 * Menggantikan `bucketId: Long?` yang dulu hanya bisa "folder kamera vs
 * satu BUCKET_ID". Sekarang mendukung album cerdas (Recent/Videos/Screenshots
 * dst) plus album folder biasa via [Bucket].
 *
 * Bukan @Serializable karena Nav3 backstack menyimpan `albumKey: String`
 * (lihat [albumKey] / [mediaScopeFromKey]) yang lebih stabil di process death.
 */
sealed interface MediaScope {
    /** Folder kamera saja (DCIM/Camera). Setara tab Gallery utama. */
    data object Camera : MediaScope

    /** Semua foto & video di device (semua folder). */
    data object AllMedia : MediaScope

    /** Hanya video di device (semua folder). */
    data object AllVideos : MediaScope

    /** Semua screenshot (Pictures/Screenshots, DCIM/Screenshots, dst). */
    data object Screenshots : MediaScope

    /** Semua screen recording (Movies/Screen recordings, ScreenRecorder, dst). */
    data object ScreenRecordings : MediaScope

    /** Media yang di-favoritkan (join ke tabel Room di layer repository). */
    data object Favorites : MediaScope

    /**
     * Item di app-level Trash (soft-delete). Layar Trash tidak memakai
     * MediaStore scan untuk isinya -- konten diamati langsung dari tabel
     * Room `trashed`. Scope ini disediakan supaya `albumKey()` &
     * `mediaScopeFromKey()` tetap total (exhaustive) dan aman.
     */
    data object Trash : MediaScope

    /** Satu folder biasa berdasarkan BUCKET_ID. */
    data class Bucket(val bucketId: Long) : MediaScope
}

// Kunci string stabil untuk album cerdas -- dipakai di NavKeys & DataStore.
const val ALBUM_KEY_RECENT = "recent"
const val ALBUM_KEY_CAMERA = "camera"
const val ALBUM_KEY_VIDEOS = "videos"
const val ALBUM_KEY_SCREENSHOTS = "screenshots"
const val ALBUM_KEY_RECORDINGS = "recordings"
const val ALBUM_KEY_FAVORITES = "favorites"
const val ALBUM_KEY_TRASH = "trash"
private const val BUCKET_KEY_PREFIX = "bucket:"

/**
 * Kunci album yang TIDAK BOLEH di-unpin oleh user. Dipakai [AlbumsViewModel]
 * untuk menolak operasi unpin & memberi hint di UI (tombol Unpin di overlay
 * hold disembunyikan untuk album-album ini).
 *
 * Rasional: Recent = pintu masuk utama semua media, Videos = filter tipe
 * penting, Favorites = kurasi user. Ketiganya tetap boleh di-reorder dalam
 * section Pinned.
 */
val LOCKED_PIN_ALBUM_KEYS: Set<String> = setOf(
    ALBUM_KEY_RECENT,
    ALBUM_KEY_VIDEOS,
    ALBUM_KEY_FAVORITES,
)

/**
 * Default 5 album yang di-pin di bagian atas tab Albums. Screen Recordings
 * SENGAJA tidak masuk default (masuk More) karena tidak semua HP punya folder
 * itu; user bisa mem-pin manual lewat long-press.
 */
val DEFAULT_PINNED_ALBUM_KEYS: List<String> = listOf(
    ALBUM_KEY_RECENT,
    ALBUM_KEY_CAMERA,
    ALBUM_KEY_VIDEOS,
    ALBUM_KEY_SCREENSHOTS,
    ALBUM_KEY_FAVORITES,
)

fun bucketAlbumKey(bucketId: Long): String = "$BUCKET_KEY_PREFIX$bucketId"

fun MediaScope.albumKey(): String = when (this) {
    MediaScope.AllMedia -> ALBUM_KEY_RECENT
    MediaScope.Camera -> ALBUM_KEY_CAMERA
    MediaScope.AllVideos -> ALBUM_KEY_VIDEOS
    MediaScope.Screenshots -> ALBUM_KEY_SCREENSHOTS
    MediaScope.ScreenRecordings -> ALBUM_KEY_RECORDINGS
    MediaScope.Favorites -> ALBUM_KEY_FAVORITES
    MediaScope.Trash -> ALBUM_KEY_TRASH
    is MediaScope.Bucket -> bucketAlbumKey(bucketId)
}

fun mediaScopeFromKey(key: String): MediaScope = when (key) {
    ALBUM_KEY_RECENT -> MediaScope.AllMedia
    ALBUM_KEY_CAMERA -> MediaScope.Camera
    ALBUM_KEY_VIDEOS -> MediaScope.AllVideos
    ALBUM_KEY_SCREENSHOTS -> MediaScope.Screenshots
    ALBUM_KEY_RECORDINGS -> MediaScope.ScreenRecordings
    ALBUM_KEY_FAVORITES -> MediaScope.Favorites
    ALBUM_KEY_TRASH -> MediaScope.Trash
    else -> if (key.startsWith(BUCKET_KEY_PREFIX)) {
        MediaScope.Bucket(key.removePrefix(BUCKET_KEY_PREFIX).toLongOrNull() ?: 0L)
    } else {
        MediaScope.Camera
    }
}
