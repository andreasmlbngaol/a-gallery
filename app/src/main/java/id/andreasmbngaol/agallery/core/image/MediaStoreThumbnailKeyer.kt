package id.andreasmbngaol.agallery.core.image

import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * Coil [Keyer] for [MediaStoreThumbnail] so fetch results can enter the memory
 * and disk caches.
 *
 * Without a keyer Coil cannot derive a cache key from the custom type and would
 * refetch on every display. The key combines the uri and the target size, so
 * the same thumbnail is not decoded repeatedly while scrolling back and forth.
 */
class MediaStoreThumbnailKeyer : Keyer<MediaStoreThumbnail> {
    override fun key(data: MediaStoreThumbnail, options: Options): String {
        val w = options.size.width.pxOrElse { 0 }
        val h = options.size.height.pxOrElse { 0 }
        return "${data.uri}#thumb=${w}x$h"
    }
}
