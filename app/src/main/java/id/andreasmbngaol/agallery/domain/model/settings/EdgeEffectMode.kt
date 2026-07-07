package id.andreasmbngaol.agallery.domain.model.settings

/**
 * Screen-edge effect mode (status bar and navigation bar areas).
 *
 * - [OFF]: no effect.
 * - [DARKEN]: tint gradient that intensifies toward the edges (lightweight, runs
 *   on all devices).
 * - [BLURRY]: glass blur (Haze) plus gradient (requires RenderEffect, API 32+).
 *
 * Ordered intentionally from lightest to heaviest.
 */
enum class EdgeEffectMode {
    OFF,
    DARKEN,
    BLURRY,
}
