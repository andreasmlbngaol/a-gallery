package id.andreasmbngaol.agallery.domain.model.ai

/**
 * How the Image Upscaler decides the RESULT size. The models always produce a
 * fixed scale (e.g. 4x) internally; this only controls how large a source we
 * feed in and what we do with that output.
 */
enum class UpscaleMode {
    /**
     * Default. Enlarge small/low-res photos (where upscaling helps most), but
     * keep already-large photos at their original size -- just cleaner/sharper.
     */
    AUTO,

    /** Always enlarge: output is the model's scale x the (capped) source. */
    ENLARGE,

    /** Keep the source's original resolution; the result is just sharper. */
    ORIGINAL_SIZE,

    /**
     * Feed the source at (near) full resolution for a genuine scale x result
     * (e.g. 1920 -> 7680). Much slower and heavier on memory.
     */
    FULL,
}
