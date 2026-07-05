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
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * Model ringan yang dikirim ke Coil sebagai `data` untuk thumbnail GRID.
 *
 * Dengan tipe khusus ini (bukan String/Uri biasa), request grid diarahkan ke
 * [MediaStoreThumbnailFetcher] alih-alih fetcher default Coil yang membuka &
 * men-decode file foto penuh.
 *
 * @property uri content:// uri item (foto atau video).
 * @property isVideo penanda video; loadThumbnail otomatis ambil 1 frame video,
 *   jadi tidak butuh decoder frame terpisah untuk grid.
 */
data class MediaStoreThumbnail(
    val uri: String,
    val isVideo: Boolean,
)

/**
 * Fetcher yang mengambil thumbnail lewat [ContentResolver.loadThumbnail]
 * (tersedia sejak API 29 = minSdk app ini).
 *
 * ## Kenapa ini jauh lebih cepat
 * `loadThumbnail` mengembalikan thumbnail berukuran kecil yang umumnya sudah
 * di-generate & di-cache oleh sistem (MediaStore). Jadi kita TIDAK perlu:
 *  - membuka file foto asli (belasan MP),
 *  - men-decode + downsample seluruh file di tiap cell saat scroll.
 *
 * Ini menghilangkan sumber utama "berat" & "lama load" saat scroll galeri besar.
 */
class MediaStoreThumbnailFetcher(
    private val contentResolver: ContentResolver,
    private val model: MediaStoreThumbnail,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Ukuran target dari request (mis. 400x400). Kalau tak terdefinisi,
        // pakai default yang aman untuk cell grid.
        val width = options.size.width.pxOrElse { DEFAULT_THUMBNAIL_PX }
            .coerceAtLeast(1)
        val height = options.size.height.pxOrElse { DEFAULT_THUMBNAIL_PX }
            .coerceAtLeast(1)

        val raw = contentResolver.loadThumbnail(
            model.uri.toUri(),
            AndroidSize(width, height),
            null,
        )

        // RGB_565: 2 byte/pixel (vs 4 byte ARGB_8888) -> setengah memori cache &
        // setengah bandwidth upload tekstur ke GPU tiap frame. loadThumbnail
        // mengembalikan ARGB_8888; convert sekali di sini (di thread background
        // Coil, bukan main thread). Foto galeri praktis tak butuh alpha, jadi
        // kualitas visual di grid sama saja.
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
            // Sudah berupa thumbnail kecil -> tandai sampled biar Coil tidak
            // coba memperlakukannya sebagai gambar resolusi penuh.
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

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
        /** Fallback ukuran thumbnail (px) kalau request tak menetapkan size. */
        private const val DEFAULT_THUMBNAIL_PX = 400
    }
}

/**
 * Keyer untuk [MediaStoreThumbnail] supaya hasil fetch bisa masuk memory &
 * disk cache. Tanpa keyer, Coil tidak tahu cara membuat cache key dari tipe
 * kustom sehingga setiap tampil akan fetch ulang (percuma).
 *
 * Cache key = uri + ukuran target, jadi thumbnail yang sama tidak di-decode
 * berkali-kali saat scroll bolak-balik.
 */
class MediaStoreThumbnailKeyer : Keyer<MediaStoreThumbnail> {
    override fun key(data: MediaStoreThumbnail, options: Options): String {
        val w = options.size.width.pxOrElse { 0 }
        val h = options.size.height.pxOrElse { 0 }
        return "${data.uri}#thumb=${w}x$h"
    }
}
