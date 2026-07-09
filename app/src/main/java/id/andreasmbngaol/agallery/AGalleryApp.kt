package id.andreasmbngaol.agallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import id.andreasmbngaol.agallery.core.ai.AccelerationConfig
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.di.appModules
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnailFetcher
import id.andreasmbngaol.agallery.core.image.MediaStoreThumbnailKeyer
import id.andreasmbngaol.agallery.data.work.TrashPurgeWorker
import id.andreasmbngaol.agallery.domain.model.settings.PerformanceMode
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * The main [Application] class.
 *
 * Implements [SingletonImageLoader.Factory] so that EVERY Coil call (AsyncImage
 * in the grid, telephoto in the viewer) uses the same single ImageLoader — the
 * one that already has [VideoFrameDecoder] registered.
 */
class AGalleryApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AGalleryApp)
            modules(appModules)
        }
        // Self-heal: if the previous run crashed natively while building an
        // XNNPACK session, permanently fall back to the CPU provider on this
        // device. Must run before any inference (which only happens on user
        // action, so onCreate is safe).
        GlobalContext.get().get<AccelerationConfig>().recoverFromCrashIfNeeded()
        TrashPurgeWorker.schedule(this)
    }

    /**
     * Releases the AI inference engine's warm session cache when the system asks
     * us to trim memory. The cache keeps a model's weights resident between runs
     * so back-to-back Background Removal / Subject Lift gestures skip the ~300ms
     * session rebuild — but a big model (IS-Net ~200MB) should
     * not linger once the app is backgrounded (TRIM_MEMORY_UI_HIDDEN) or the
     * device is under pressure. releaseCache() is a no-op while a run is still in
     * flight. Wrapped in runCatching so trimming can never crash the app.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { GlobalContext.get().get<InferenceEngine>().releaseCache() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { GlobalContext.get().get<InferenceEngine>().releaseCache() }
    }

    /**
     * The global Coil [ImageLoader]. [VideoFrameDecoder] MUST be registered so
     * video URIs (content://) can be decoded into a single frame to use as the
     * grid cover. Without it, video items fail to decode and render as an empty
     * (black/white) box, while photos keep working.
     *
     * ## Performance tuning
     * - The memory cache is capped at ~25% of the app's RAM: enough for smooth
     *   scrolling without triggering aggressive GC or overheating the device on
     *   galleries with thousands of photos.
     * - The disk cache is enabled so thumbnails don't have to be re-decoded on
     *   every app launch.
     * - The crossfade is shortened (150ms) to keep the animation feeling light.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(MediaStoreThumbnailFetcher.Factory(context.contentResolver))
                add(MediaStoreThumbnailKeyer())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, resolveMemoryCachePercent())
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .build()
    }

    /**
     * Reads the stored [PerformanceMode] (once, when the ImageLoader is built)
     * and maps it to a RAM percentage for the memory cache. Wrapped in
     * try/catch so a preference read failure can NEVER crash the app — it falls
     * back to the balanced default. A mode change at runtime only takes full
     * effect after a restart (Coil's memory cache size is set at build time and
     * cannot be resized).
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
