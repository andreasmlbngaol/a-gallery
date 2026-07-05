package id.andreasmbngaol.agallery.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.andreasmbngaol.agallery.core.permission.AllFilesAccess
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Worker harian yang menghapus PERMANEN item Trash yg umurnya > 30 hari.
 *
 * Hanya efektif bila app punya All-files access, karena penghapusan di
 * background TIDAK bisa memunculkan dialog konfirmasi sistem. Kalau izin belum
 * ada, worker jadi no-op (item tetap tersimpan sampai user purge manual di
 * layar Trash).
 */
class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val mediaRepository: MediaRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            if (AllFilesAccess.isGranted()) {
                mediaRepository.autoPurgeExpiredDirectly(RETENTION_DAYS)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val RETENTION_DAYS = 30
        private const val UNIQUE_NAME = "trash-auto-purge"

        /** Jadwalkan purge harian (idempoten; dipanggil sekali saat app start). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(
                1, TimeUnit.DAYS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
