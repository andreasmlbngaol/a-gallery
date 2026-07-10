package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A single detected face, expressed as a rectangle NORMALIZED to the source
 * image (0..1 on each axis, relative to [FaceDetectionResult.imageWidth] /
 * [FaceDetectionResult.imageHeight]). Normalized coordinates let the UI overlay
 * the box on a scaled/letterboxed preview without knowing the display size.
 */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Result of running on-device face detection on a source image: the decoded
 * image dimensions the boxes are relative to, and one [FaceBox] per detected
 * face (empty when none were found or the image couldn't be read, in which case
 * [imageWidth] / [imageHeight] are 0).
 */
data class FaceDetectionResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<FaceBox>,
) {
    companion object {
        /** Empty result used when the image is unreadable or has no faces. */
        val EMPTY = FaceDetectionResult(0, 0, emptyList())
    }
}
