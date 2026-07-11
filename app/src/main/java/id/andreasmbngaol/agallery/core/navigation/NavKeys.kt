package id.andreasmbngaol.agallery.core.navigation

import androidx.navigation3.runtime.NavKey
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import kotlinx.serialization.Serializable

/**
 * Type-safe Nav3 route keys; one [Screen] per destination.
 *
 * Every route is [Serializable] so the back stack can be saved and restored
 * across process death via `rememberNavBackStack`.
 */
sealed interface Screen : NavKey {

    /**
     * The single root destination hosting the Settings/Gallery/Albums/Tools tab
     * pager.
     */
    @Serializable
    data object Home : Screen

    /**
     * Full-screen photo/video viewer for a media item.
     *
     * @property mediaId the id of the tapped media item.
     * @property initialIndex the position of the item in the source grid.
     * @property sortOrder the sort order of the source list, so paging matches.
     * @property albumKey the source album key (e.g. `"camera"`, `"recent"`,
     *   `"favorites"`, or `"bucket:12345"`); `null` means the main Gallery tab.
     */
    @Serializable
    data class PhotoViewer(
        val mediaId: Long,
        val initialIndex: Int,
        val sortOrder: GallerySortOrder,
        val albumKey: String? = null,
    ) : Screen

    /**
     * Contents of a single album (folder or smart), shown as a scoped grid.
     *
     * @property albumKey the key identifying the album to display.
     * @property albumName the album's display name for the title.
     */
    @Serializable
    data class AlbumDetail(
        val albumKey: String,
        val albumName: String,
    ) : Screen

    /**
     * App-level Trash screen.
     *
     * Items are observed from the Room `trashed` table. This is separate from
     * [AlbumDetail] because it has dedicated actions (restore and delete
     * forever) and is not a regular MediaStore scope.
     */
    @Serializable
    data object Trash : Screen

    /**
     * The "create new album" flow: the user names the album, then picks photos
     * from existing albums to copy into it.
     *
     * Cancelling creates no folder — the folder is only created when the first
     * photo is actually copied — so there is never an empty folder to clean up.
     */
    @Serializable
    data object CreateAlbum : Screen

    /** QR code generator screen, opened from the Tools tab. */
    @Serializable
    data object QrGenerator : Screen

    /**
     * On-device AI models management screen, opened from Settings. Lets the user
     * import / re-import / delete `.onnx` model files used by AI features.
     */
    @Serializable
    data object AiModels : Screen

    /**
     * Background Remover for a single image, opened from the photo viewer.
     *
     * @property mediaUri the source image uri to process.
     * @property displayName the source file name, used to derive the saved name.
     */
    @Serializable
    data class BackgroundRemover(
        val mediaUri: String,
        val displayName: String,
    ) : Screen


    /**
     * Image Upscaler for a single image, opened from the photo viewer.
     *
     * @property mediaUri the source image uri to process.
     * @property displayName the source file name, used to derive the saved name.
     */
    @Serializable
    data class ImageUpscale(
        val mediaUri: String,
        val displayName: String,
    ) : Screen

    /**
     * Face Restore for a single image, opened from the photo viewer.
     *
     * @property mediaUri the source image uri to process.
     * @property displayName the source file name, used to derive the saved name.
     */
    @Serializable
    data class FaceRestore(
        val mediaUri: String,
        val displayName: String,
    ) : Screen

    /**
     * Photo Enhance for a single image, opened from the photo viewer.
     *
     * @property mediaUri the source image uri to process.
     * @property displayName the source file name, used to derive the saved name.
     */
    @Serializable
    data class PhotoEnhance(
        val mediaUri: String,
        val displayName: String,
    ) : Screen

    /**
     * Auto Enhance for a single image, opened from the photo viewer. Runs the
     * one-tap Face Restore -> Enhance -> Upscale pipeline.
     *
     * @property mediaUri the source image uri to process.
     * @property displayName the source file name, used to derive the saved name.
     */
    @Serializable
    data class AutoEnhance(
        val mediaUri: String,
        val displayName: String,
    ) : Screen
}
