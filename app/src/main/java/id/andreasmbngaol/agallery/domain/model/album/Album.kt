package id.andreasmbngaol.agallery.domain.model.album

import id.andreasmbngaol.agallery.domain.model.media.MediaScope

/**
 * A single album shown in the Albums tab. It can be a folder-backed album
 * (bucket) or a smart album (Recent, Camera, Videos, Screenshots,
 * ScreenRecordings, Favorites).
 *
 * @property key Stable identifier across process death and rotation; used as the
 *   navigation key when opening album detail and for pin/order state in DataStore.
 * @property scope Query criteria understood by the media data source.
 * @property name Display name of the album.
 * @property coverUri URI of the cover thumbnail, or null when the album is empty.
 * @property photoCount Number of photos in the album.
 * @property videoCount Number of videos in the album.
 * @property isSmart Distinguishes smart albums from real folders; used by the
 *   Move/Copy picker in the viewer, which only lists real folders.
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
    /** Total item count: photos plus videos. */
    val itemCount: Int get() = photoCount + videoCount
}
