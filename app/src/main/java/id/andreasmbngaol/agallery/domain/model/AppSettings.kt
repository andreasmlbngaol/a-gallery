package id.andreasmbngaol.agallery.domain.model

/** Batas & default jumlah kolom grid galeri (dipakai lintas layer). */
const val MIN_GRID_COLUMNS = 3
const val MAX_GRID_COLUMNS = 5
const val DEFAULT_GRID_COLUMNS = 4

/**
 * Preferensi aplikasi (domain) \u2014 sumber kebenaran tunggal, dipersist via DataStore.
 *
 * [edgeEffectMode] null = ikuti "default cerdas" (hybrid): FROSTED di API >= 32,
 * GRADIENT di bawahnya. Resolusi final butuh Build.VERSION, jadi dilakukan di
 * layer presentation; domain cukup menyimpan pilihan mentah user.
 *
 * [gridColumns] jumlah kolom grid galeri (3..5, default 4 = nilai tengah).
 * [sortOrder] urutan sortir media; dipersist supaya TETAP walau app ditutup.
 * [performanceMode] trade-off RAM vs kelancaran scroll (prefetch + cache).
 * [pinnedAlbumKeys] daftar kunci album yang di-pin di tab Albums (urutan =
 * urutan tampilan). null = pakai [DEFAULT_PINNED_ALBUM_KEYS].
 */
data class AppSettings(
    val edgeEffectMode: EdgeEffectMode? = null,
    // Gaya komponen UI (Solid/Frosted/Glass). null = default cerdas, di-resolve
    // di layer presentation (GLASS -> FROSTED otomatis di perangkat < API 33).
    val componentStyle: ComponentStyle? = null,
    val gridColumns: Int = DEFAULT_GRID_COLUMNS,
    val sortOrder: GallerySortOrder = GallerySortOrder.DateDesc,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val pinnedAlbumKeys: List<String>? = null,
)
