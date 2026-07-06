package id.andreasmbngaol.agallery.domain.model

/**
 * Metadata tambahan sebuah media yang TIDAK ikut dimuat di [MediaItem] supaya
 * query grid tetap ringan. Diambil on-demand saat user membuka panel detail
 * di viewer (swipe ke atas). Berlaku untuk foto & video.
 *
 * Field inti (ukuran, dimensi, path) selalu ada. Field opsional (kamera/EXIF,
 * lokasi, teknis video) bernilai null kalau media tsb memang tidak
 * menyimpannya (mis. screenshot / hasil download) -> UI menyembunyikan baris
 * yang null, dan hanya field inti yang ditampilkan "-" saat kosong.
 *
 * Nilai teknis yang locale-independent (aperture, shutter, bitrate, dsb)
 * sudah diformat rapi di data layer. Yang butuh lokalisasi (flash) atau
 * format lokal (tanggal, koordinat) dibiarkan mentah untuk diformat di UI.
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

    // ---- Kamera / EXIF (khusus foto) ----
    /** Pabrikan kamera, mis. "Google". */
    val cameraMake: String? = null,
    /** Model kamera/HP, mis. "Pixel 8". */
    val cameraModel: String? = null,
    /** Aperture terformat, mis. "f/1.8". */
    val aperture: String? = null,
    /** Shutter speed terformat, mis. "1/125 s". */
    val shutterSpeed: String? = null,
    /** ISO, mis. "100". */
    val iso: String? = null,
    /** Focal length terformat, mis. "24 mm". */
    val focalLength: String? = null,
    /** Apakah flash menyala. null kalau tak tercatat. */
    val flashFired: Boolean? = null,

    // ---- Waktu pengambilan (EXIF DateTimeOriginal) ----
    /** Epoch detik saat media diambil (beda dgn tanggal ditambahkan). */
    val dateTakenEpochSeconds: Long? = null,

    // ---- Lokasi GPS ----
    val latitude: Double? = null,
    val longitude: Double? = null,

    // ---- Teknis video (MediaMetadataRetriever / MediaExtractor) ----
    /** Frame rate terformat, mis. "30 fps". */
    val frameRate: String? = null,
    /** Bitrate terformat, mis. "12.0 Mbps". */
    val bitrate: String? = null,
    /** Codec video ramah baca, mis. "H.264". */
    val videoCodec: String? = null,
    /** Codec audio ramah baca, mis. "AAC". */
    val audioCodec: String? = null,
)
