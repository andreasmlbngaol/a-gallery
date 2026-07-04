package id.andreasmbngaol.agallery.domain.model

/**
 * Mode efek di tepi layar (area status bar & navigation bar).
 * OFF = tanpa efek; DARKEN = gradasi tint yang menguat ke tepi (ringan, jalan di
 * semua perangkat); BLURRY = blur kaca (Haze) + gradasi (butuh RenderEffect, API 32+).
 * Urutan sengaja ringan -> berat.
 */
enum class EdgeEffectMode {
    OFF,
    DARKEN,
    BLURRY,
}
