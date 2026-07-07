package id.andreasmbngaol.agallery.domain.model.metadata

/**
 * Categories of metadata that can be stripped from a photo (feature 1.4.0).
 *
 * - [LOCATION]: all GPS tags (coordinates, direction, speed, etc.).
 * - [CAMERA]: device and capture settings (make/model, aperture, shutter, ISO,
 *   focal length, flash, capture date, etc.).
 * - [ALL]: everything above plus other personal info (artist, copyright,
 *   comments, maker note, etc.). Orientation is intentionally preserved so the
 *   photo does not end up rotated after stripping.
 */
enum class MetadataCategory { LOCATION, CAMERA, ALL }
