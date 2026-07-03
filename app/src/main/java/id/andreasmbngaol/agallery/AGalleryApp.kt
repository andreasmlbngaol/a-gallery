package id.andreasmbngaol.agallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnailFetcher
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnailKeyer
import id.andreasmbngaol.agallery.core.di.appModules
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Kelas Application utama.
 *
 * Mengimplementasikan [SingletonImageLoader.Factory] supaya SEMUA pemanggilan
 * Coil (AsyncImage di grid, telephoto di viewer) memakai satu ImageLoader yang
 * sama — yang sudah didaftari [VideoFrameDecoder].
 */
class AGalleryApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AGalleryApp)
            modules(appModules)
        }
    }

    /**
     * ImageLoader global untuk Coil. [VideoFrameDecoder] wajib didaftarkan agar
     * URI video (content://) bisa di-decode jadi 1 frame sebagai sampul di grid.
     * Tanpa decoder ini, item video gagal decode sehingga hanya tampil kotak
     * kosong (hitam/putih), sementara foto tetap normal.
     *
     * ## Tuning performa
     * - memoryCache dibatasi ~25% RAM app: cukup buat scroll mulus tapi nggak
     *   bikin GC agresif / HP kepanasan di galeri ribuan foto.
     * - diskCache aktif: thumbnail nggak perlu decode ulang tiap buka app.
     * - crossfade dipendekin (150ms) biar animasi terasa lebih ringan.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Fetcher kustom: thumbnail GRID diambil dari thumbnail bawaan
                // MediaStore (loadThumbnail, API 29+) -> jauh lebih ringan &
                // cepat daripada decode file foto penuh. Keyer WAJIB supaya
                // hasilnya masuk cache (memory + disk).
                add(MediaStoreThumbnailFetcher.Factory(context.contentResolver))
                add(MediaStoreThumbnailKeyer())
                // Tetap perlu untuk uri video biasa (mis. cover album) yang
                // TIDAK lewat MediaStoreThumbnail.
                add(VideoFrameDecoder.Factory())
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    // Ukuran cache mengikuti PerformanceMode pilihan user: makin
                    // agresif = makin banyak thumbnail ditahan di RAM supaya scroll
                    // balik tidak decode ulang. Dibaca SEKALI saat ImageLoader
                    // dibuat; perubahan mode berlaku penuh setelah app di-restart.
                    .maxSizePercent(context, resolveMemoryCachePercent())
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .build()
    }

    /**
     * Baca [PerformanceMode] tersimpan (sekali, saat ImageLoader dibuat) lalu
     * petakan ke persentase RAM untuk memory cache. Dibungkus try/catch supaya
     * kegagalan baca preferensi TIDAK pernah bikin app crash — jatuh ke default
     * seimbang. Perubahan mode saat runtime baru berlaku penuh setelah restart
     * (ukuran memory cache Coil di-set saat build, tidak bisa di-resize).
     */
    private fun resolveMemoryCachePercent(): Double {
        val mode = try {
            val repository = GlobalContext.get().get<SettingsRepository>()
            runBlocking { repository.settings.first().performanceMode }
        } catch (_: Throwable) {
            PerformanceMode.BALANCED
        }
        return when (mode) {
            PerformanceMode.LOW -> 0.20
            PerformanceMode.BALANCED -> 0.30
            PerformanceMode.HIGH -> 0.50
        }
    }
}
