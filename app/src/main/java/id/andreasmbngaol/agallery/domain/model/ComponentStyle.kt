package id.andreasmbngaol.agallery.domain.model

/**
 * Gaya visual komponen UI sistem (floating nav bar, tombol mengambang, island
 * viewer, dsb). Sengaja DIPISAH dari [EdgeEffectMode] yang khusus efek TEPI
 * layar (status/nav bar), supaya glass yang berat tidak nyangkut ke setting
 * edge effect.
 *
 * SOLID   = bar/tombol solid semi-transparan. Paling ringan (tanpa blur),
 *   paling mulus di perangkat apa pun.
 * FROSTED = kaca beku translusen + tint, TANPA sampling konten live. Tetap
 *   berkesan kaca tapi jauh lebih ringan; cocok untuk kebanyakan perangkat.
 * GLASS   = liquid glass (refraction Kyant) yang membiaskan konten di
 *   belakangnya secara real-time. Paling cakep TAPI paling berat: butuh
 *   RuntimeShader (Android 13 / API 33+) dan memakai GPU tiap frame. Di bawah
 *   API 33 otomatis turun ke FROSTED.
 */
enum class ComponentStyle {
    SOLID,
    FROSTED,
    GLASS,
}
