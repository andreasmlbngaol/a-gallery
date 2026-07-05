package id.andreasmbngaol.agallery.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Daftar permission media yang adaptif terhadap versi Android.
 * minSdk = 29, jadi harus cover 3 rezim.
 */
object MediaPermissions {
    /**
     * Daftar permission yang diminta lewat runtime request.
     *
     * SENGAJA TIDAK menyertakan READ_MEDIA_VISUAL_USER_SELECTED. Kalau
     * permission itu ikut diminta, di Android 14+ sistem malah menampilkan
     * PHOTO PICKER ("pilih foto") ketika request diulang — bukan dialog izin
     * standar "Allow all / Select photos / Don't allow". Karena app ini WAJIB
     * akses PENUH, kita cukup minta IMAGES + VIDEO supaya dialog izin standar
     * (yang punya opsi "Allow all") yang muncul. Untuk pindah dari "limited" ke
     * "Allow all", user diarahkan ke App Settings oleh gate.
     *
     * Sama dengan [essential] — dipisah agar niatnya eksplisit di pemanggil.
     */
    fun required(): List<String> = essential()

    /**
     * Permission media yang WAJIB granted (akses PENUH). Di API 33+ harus
     * IMAGES & VIDEO; di bawahnya READ_EXTERNAL_STORAGE. Akses "sebagian"
     * (VISUAL_USER_SELECTED di Android 14+) sengaja TIDAK dihitung cukup karena
     * galeri butuh melihat seluruh media.
     */
    fun essential(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True bila semua permission [essential] sudah granted saat ini. */
    fun hasEssential(context: Context): Boolean = essential().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Evaluasi hasil runtime request: apakah semua [essential] granted? */
    fun essentialGranted(result: Map<String, Boolean>): Boolean =
        essential().all { result[it] == true }
}
