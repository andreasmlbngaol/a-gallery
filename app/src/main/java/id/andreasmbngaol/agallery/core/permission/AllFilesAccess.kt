package id.andreasmbngaol.agallery.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Helper "All files access" (MANAGE_EXTERNAL_STORAGE).
 *
 * Dengan izin ini app bisa hapus/rename/pindah file media milik app LAIN
 * secara LANGSUNG tanpa dialog konfirmasi sistem (scoped storage di-bypass),
 * dan bisa menghapus di background (dipakai auto-purge Trash 30 hari via
 * WorkManager, yang mustahil lewat dialog SAF).
 *
 * Di bawah Android 11 (API 30) konsep ini tidak ada -> anggap sudah "granted"
 * karena READ/WRITE_EXTERNAL_STORAGE lama sudah cukup.
 */
object AllFilesAccess {
    /** True bila app boleh akses seluruh file (atau OS < 11 yg tak butuh). */
    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Apakah OS ini punya konsep All-files access (API 30+). */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Intent menuju layar "All files access" khusus app ini. Kalau layar khusus
     * tak tersedia di perangkat, fallback ke daftar umum semua app.
     */
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
