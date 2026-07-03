package id.andreasmbngaol.agallery.domain.model

/**
 * Metadata tambahan sebuah media yang TIDAK ikut dimuat di [MediaItem] supaya
 * query grid tetap ringan. Diambil on-demand saat user membuka panel detail
 * di viewer (swipe ke atas). Berlaku untuk foto & video.
 *
 * Pure Kotlin — JANGAN import android.* di sini.
 */
data class MediaDetails(
    /** Ukuran file dalam byte. 0 kalau tak diketahui. */
    val sizeBytes: Long,
    /** Lebar dalam piksel. 0 kalau tak diketahui. */
    val width: Int,
    /** Tinggi dalam piksel. 0 kalau tak diketahui. */
    val height: Int,
    /** Folder relatif tempat file (mis. "DCIM/Camera/"). Kosong kalau tak ada. */
    val relativePath: String,
)
