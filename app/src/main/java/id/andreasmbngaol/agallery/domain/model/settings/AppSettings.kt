package id.andreasmbngaol.agallery.domain.model.settings

import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality

/**
 * Application preferences (domain) -- the single source of truth, persisted
 * via DataStore.
 *
 * @property edgeEffectMode Screen-edge effect. null means follow the smart
 *   default (hybrid): FROSTED on API >= 32, GRADIENT below. Final resolution
 *   needs Build.VERSION and is done in the presentation layer; the domain only
 *   stores the raw user choice.
 * @property componentStyle UI component style (Solid/Frosted/Glass). null means
 *   the smart default, resolved in the presentation layer (GLASS falls back to
 *   FROSTED automatically on devices below API 33).
 * @property gridColumns Gallery grid column count (3..5, default 4).
 * @property sortOrder Media sort order; persisted so it survives app restarts.
 * @property performanceMode RAM vs. scroll-smoothness trade-off (prefetch + cache).
 * @property pinnedAlbumKeys Album keys pinned in the Albums tab (list order =
 *   display order). null means use the default pinned album keys.
 */
data class AppSettings(
    val edgeEffectMode: EdgeEffectMode? = null,
    val componentStyle: ComponentStyle? = null,
    val gridColumns: Int = DEFAULT_GRID_COLUMNS,
    val sortOrder: GallerySortOrder = GallerySortOrder.DateDesc,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val pinnedAlbumKeys: List<String>? = null,
    val liftModelId: String? = null,
    val liftQuality: RemovalQuality = RemovalQuality.ECO,
)
