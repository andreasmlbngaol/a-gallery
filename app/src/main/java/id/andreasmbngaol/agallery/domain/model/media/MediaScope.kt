package id.andreasmbngaol.agallery.domain.model.media

/**
 * Scope of media shown in the grid / viewer.
 *
 * Replaces the old `bucketId: Long?` that could only express "camera folder vs.
 * a single BUCKET_ID". It now supports smart albums (Recent/Videos/Screenshots,
 * etc.) plus ordinary folder albums via [Bucket].
 *
 * Not @Serializable because the Nav3 backstack stores `albumKey: String` (mapped
 * by `MediaScope.albumKey()` / `mediaScopeFromKey()` in the album package), which
 * is more stable across process death.
 */
sealed interface MediaScope {
    /** Camera folder only (DCIM/Camera). Equivalent to the main Gallery tab. */
    data object Camera : MediaScope

    /** All photos and videos on the device (all folders). */
    data object AllMedia : MediaScope

    /** Videos only, across all folders on the device. */
    data object AllVideos : MediaScope

    /** All screenshots (Pictures/Screenshots, DCIM/Screenshots, etc.). */
    data object Screenshots : MediaScope

    /** All screen recordings (Movies/Screen recordings, ScreenRecorder, etc.). */
    data object ScreenRecordings : MediaScope

    /** Favorited media (joined against the Room table in the repository layer). */
    data object Favorites : MediaScope

    /**
     * Items in the app-level Trash (soft-delete). The Trash screen does not use a
     * MediaStore scan for its content -- it observes the Room `trashed` table
     * directly. This scope exists so that `MediaScope.albumKey()` and
     * `mediaScopeFromKey()` stay total (exhaustive) and safe.
     */
    data object Trash : MediaScope

    /** A single ordinary folder identified by BUCKET_ID. */
    data class Bucket(val bucketId: Long) : MediaScope
}
