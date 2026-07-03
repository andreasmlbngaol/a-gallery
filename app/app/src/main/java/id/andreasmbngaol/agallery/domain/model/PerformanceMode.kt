package id.andreasmbngaol.agallery.domain.model

/**
 * Trade-off perilaku loading galeri: seberapa agresif thumbnail di-prefetch dan
 * seberapa banyak RAM dipakai untuk memory cache demi kelancaran scroll.
 *
 * LOW = hemat RAM. Prefetch minim, thumbnail dimuat saat mendekat. Cocok untuk
 *   perangkat kelas bawah / RAM kecil.
 * BALANCED = default seimbang. Prefetch beberapa baris di depan viewport.
 * HIGH = boros RAM demi mulus. Prefetch banyak baris (di depan & di atas) plus
 *   memory cache lebih besar, sehingga scroll jarang menunggu decode.
 */
enum class PerformanceMode {
    LOW,
    BALANCED,
    HIGH,
}
