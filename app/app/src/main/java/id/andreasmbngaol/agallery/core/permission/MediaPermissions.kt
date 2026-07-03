package id.andreasmbngaol.agallery.core.permission

import android.Manifest
import android.os.Build

/**
 * Daftar permission media yang adaptif terhadap versi Android.
 * minSdk = 29, jadi harus cover 3 rezim.
 */
object MediaPermissions {
    fun required(): List<String> = when {
        Build.VERSION.SDK_INT >= 34 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
