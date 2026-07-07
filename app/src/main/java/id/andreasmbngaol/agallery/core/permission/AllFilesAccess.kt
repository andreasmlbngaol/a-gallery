package id.andreasmbngaol.agallery.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Helper for the "All files access" permission (`MANAGE_EXTERNAL_STORAGE`).
 *
 * With this permission the app can delete, rename, and move media owned by
 * other apps directly, without a per-action system confirmation dialog (scoped
 * storage is bypassed), and can delete in the background — required by the
 * 30-day Trash auto-purge run through WorkManager, which cannot use the SAF
 * dialog.
 *
 * The concept does not exist below Android 11 (API 30), where the legacy
 * `READ`/`WRITE_EXTERNAL_STORAGE` permissions suffice, so it is treated as
 * already granted there.
 */
object AllFilesAccess {

    /**
     * Returns whether the app may access all files, or is running on an OS below
     * API 30 that does not require this permission.
     */
    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Returns whether this OS has the All-files access concept (API 30+). */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Builds an intent to this app's "All files access" settings screen, falling
     * back to the general list of all apps if the app-specific screen is
     * unavailable on the device.
     *
     * @param context the context whose package name targets the settings screen.
     * @return the intent to launch.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun settingsIntent(context: Context): Intent {
        return try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }
}
