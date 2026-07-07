package id.andreasmbngaol.agallery.domain.model.album

import id.andreasmbngaol.agallery.domain.model.media.MediaScope

/** Stable string keys for smart albums -- used in NavKeys and DataStore. */
const val ALBUM_KEY_RECENT = "recent"
const val ALBUM_KEY_CAMERA = "camera"
const val ALBUM_KEY_VIDEOS = "videos"
const val ALBUM_KEY_SCREENSHOTS = "screenshots"
const val ALBUM_KEY_RECORDINGS = "recordings"
const val ALBUM_KEY_FAVORITES = "favorites"
const val ALBUM_KEY_TRASH = "trash"
private const val BUCKET_KEY_PREFIX = "bucket:"

/**
 * Album keys the user is NOT allowed to unpin. Used by
 * [id.andreasmbngaol.agallery.presentation.albums.AlbumsViewModel] to reject
 * unpin operations and hint the UI (the Unpin button in the hold overlay is
 * hidden for these albums).
 *
 * Rationale: Recent is the main entry point to all media, Videos is an important
 * type filter, and Favorites is the user's curation. All three can still be
 * reordered within the Pinned section.
 */
val LOCKED_PIN_ALBUM_KEYS: Set<String> = setOf(
    ALBUM_KEY_RECENT,
    ALBUM_KEY_VIDEOS,
    ALBUM_KEY_FAVORITES,
)

/**
 * The default five albums pinned at the top of the Albums tab. Screen Recordings
 * is intentionally excluded from the default (it goes under More) because not
 * every phone has that folder; the user can pin it manually via long-press.
 */
val DEFAULT_PINNED_ALBUM_KEYS: List<String> = listOf(
    ALBUM_KEY_RECENT,
    ALBUM_KEY_CAMERA,
    ALBUM_KEY_VIDEOS,
    ALBUM_KEY_SCREENSHOTS,
    ALBUM_KEY_FAVORITES,
)

/** Builds the album key for an ordinary folder identified by [bucketId]. */
fun bucketAlbumKey(bucketId: Long): String = "$BUCKET_KEY_PREFIX$bucketId"

/** Maps a [MediaScope] to its stable album key. */
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

/** Resolves an album key back into its [MediaScope]; unknown keys fall back to Camera. */
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
