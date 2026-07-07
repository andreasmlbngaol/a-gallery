package id.andreasmbngaol.agallery.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central definition of the media read permissions the app requires, adapted to
 * the three Android permission regimes the app spans (minSdk 29).
 *
 * The app needs *full* access to the media library, so partial access
 * (`READ_MEDIA_VISUAL_USER_SELECTED`, introduced on Android 14) is
 * intentionally never requested nor treated as sufficient.
 */
object MediaPermissions {

    /**
     * Returns the permissions to pass to a runtime permission request.
     *
     * `READ_MEDIA_VISUAL_USER_SELECTED` is intentionally excluded: including it
     * makes Android 14+ show the photo picker on a repeated request instead of
     * the standard "Allow all / Select photos / Don't allow" dialog. Because the
     * app requires full access, requesting only images and video keeps the
     * standard dialog — the one that offers "Allow all". Moving from "limited"
     * to "Allow all" is handled by routing the user to App Settings.
     *
     * Identical to [essential]; kept separate so the intent is explicit at the
     * call site.
     *
     * @return the permission strings to request at runtime.
     */
    fun required(): List<String> = essential()

    /**
     * Returns the media permissions that must be granted for full library
     * access: `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` on API 33+, and
     * `READ_EXTERNAL_STORAGE` below that.
     *
     * @return the permission strings that constitute full access.
     */
    fun essential(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Returns whether every [essential] permission is currently granted.
     *
     * @param context the context used to check permission state.
     * @return `true` when all essential permissions are granted.
     */
    fun hasEssential(context: Context): Boolean = essential().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Evaluates the result of a runtime permission request.
     *
     * @param result the per-permission grant map returned by the request.
     * @return `true` when every [essential] permission was granted.
     */
    fun essentialGranted(result: Map<String, Boolean>): Boolean =
        essential().all { result[it] == true }
}
