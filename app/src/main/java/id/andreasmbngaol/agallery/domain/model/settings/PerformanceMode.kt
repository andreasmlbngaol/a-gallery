package id.andreasmbngaol.agallery.domain.model.settings

/**
 * Gallery loading trade-off: how aggressively thumbnails are prefetched and how
 * much RAM is spent on the memory cache for smooth scrolling.
 *
 * - [LOW]: saves RAM. Minimal prefetch; thumbnails load as they approach. Suits
 *   low-end devices / small RAM.
 * - [BALANCED]: the balanced default. Prefetches a few rows ahead of the viewport.
 * - [HIGH]: spends RAM for smoothness. Prefetches many rows (ahead and above)
 *   plus a larger memory cache, so scrolling rarely waits on decoding.
 */
enum class PerformanceMode {
    LOW,
    BALANCED,
    HIGH,
}
