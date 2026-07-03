package id.andreasmbngaol.agallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import id.andreasmbngaol.agallery.core.di.appModules
import org.koin.android.ext.koin.androidContext
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
                add(VideoFrameDecoder.Factory())
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .build()
    }
}
