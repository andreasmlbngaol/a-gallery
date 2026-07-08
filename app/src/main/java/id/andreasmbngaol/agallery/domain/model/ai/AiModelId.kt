package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Stable identifier for an AI model (e.g. `"isnet-general-use"`).
 *
 * The same value is used as the catalog key, the on-disk file-name stem under the
 * model directory, and the key persisted for the user's per-feature model
 * selection. Wrapped in an inline value class so it is type-safe at call sites
 * without boxing overhead.
 */
@JvmInline
value class AiModelId(val value: String)
