package id.andreasmbngaol.agallery.domain.model.settings

/**
 * Visual style of system UI components (floating nav bar, floating buttons,
 * viewer island, etc.). Deliberately kept separate from [EdgeEffectMode], which
 * only governs screen-edge effects (status/nav bar), so the heavy glass style
 * does not leak into the edge-effect setting.
 *
 * - [SOLID]: solid semi-transparent bars/buttons. Lightest (no blur) and
 *   smoothest on any device.
 * - [FROSTED]: translucent frosted glass with tint, without sampling live
 *   content. Still glass-like but far lighter; suitable for most devices.
 * - [GLASS]: liquid glass (Kyant refraction) that refracts the content behind it
 *   in real time. The best looking but the heaviest: requires RuntimeShader
 *   (Android 13 / API 33+) and uses the GPU every frame. Falls back to FROSTED
 *   automatically below API 33.
 */
enum class ComponentStyle {
    SOLID,
    FROSTED,
    GLASS,
}
