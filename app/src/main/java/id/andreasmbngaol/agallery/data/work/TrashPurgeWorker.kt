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
 * Daily worker that PERMANENTLY deletes Trash items older than 30 days.
 *
 * Only effective when the app has All-files access, because a background
 * deletion CANNOT show the system confirmation dialog. Without the permission
 * the worker is a no-op (items stay until the user purges them manually from the
 * Trash screen).
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

        /** Schedules the daily purge (idempotent; called once on app start). */
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
