package id.andreasmbngaol.agallery.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.util.Size as AndroidSize
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * Coil [Fetcher] that loads thumbnails via [ContentResolver.loadThumbnail],
 * available since API 29 (this app's minSdk).
 *
 * ## Why this is much faster
 * `loadThumbnail` returns a small thumbnail that the system (MediaStore) has
 * usually already generated and cached, so the fetcher never has to:
 * - open the original multi-megapixel photo file, or
 * - decode and downsample the whole file for every cell while scrolling.
 *
 * This removes the main source of jank and slow loads when scrolling a large
 * gallery.
 *
 * @property contentResolver resolver used to load system thumbnails.
 * @property model the requested thumbnail descriptor.
 * @property options the Coil request options, including the target size.
 */
class MediaStoreThumbnailFetcher(
    private val contentResolver: ContentResolver,
    private val model: MediaStoreThumbnail,
    private val options: Options,
) : Fetcher {

    /**
     * Loads the thumbnail and returns it as an already-sampled bitmap.
     *
     * The bitmap is converted to [Bitmap.Config.RGB_565] (2 bytes per pixel
     * versus 4 for `ARGB_8888`), halving cache memory and per-frame GPU upload
     * bandwidth. Gallery photos rarely need an alpha channel, so grid quality is
     * unaffected; the conversion runs on Coil's background thread. The result is
     * flagged sampled so Coil does not treat it as a full-resolution image.
     */
    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_THUMBNAIL_PX }
            .coerceAtLeast(1)
        val height = options.size.height.pxOrElse { DEFAULT_THUMBNAIL_PX }
            .coerceAtLeast(1)

        val raw = contentResolver.loadThumbnail(
            model.uri.toUri(),
            AndroidSize(width, height),
            null,
        )

        val bitmap = if (raw.config == Bitmap.Config.RGB_565) {
            raw
        } else {
            val converted = raw.copy(Bitmap.Config.RGB_565, false)
            if (converted != null) {
                raw.recycle()
                converted
            } else {
                raw
            }
        }

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    /**
     * Factory that creates a [MediaStoreThumbnailFetcher] for each
     * [MediaStoreThumbnail] request.
     *
     * @property contentResolver resolver passed to every created fetcher.
     */
    class Factory(
        private val contentResolver: ContentResolver,
    ) : Fetcher.Factory<MediaStoreThumbnail> {
        override fun create(
            data: MediaStoreThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MediaStoreThumbnailFetcher(contentResolver, data, options)
    }

    companion object {
        /** Fallback thumbnail size in pixels when a request omits its size. */
        private const val DEFAULT_THUMBNAIL_PX = 400
    }
}
