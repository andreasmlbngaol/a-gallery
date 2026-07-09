package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Describes how images are fed into a model and how its output is read back.
 * Every value is intrinsic to a given `.onnx` export and is declared up-front in
 * the [ModelCatalog] — it is never guessed at runtime.
 *
 * Normalization is applied per channel as `(pixel / 255 - mean) / std`.
 *
 * For SEGMENTATION models the output is a single-channel mask. For UPSCALE
 * models the output is a full RGB image whose spatial size is the input size
 * multiplied by [scaleFactor]; those models are always fed at their native,
 * fixed input size and processed in tiles (see the image-upscale processor).
 *
 * @property inputName name of the image input tensor, or empty to fall back to
 *   the session's first input.
 * @property outputName name of the output tensor, or empty to fall back to the
 *   session's first output.
 * @property inputWidth expected input width, in pixels.
 * @property inputHeight expected input height, in pixels.
 * @property layout channel ordering of the input tensor.
 * @property mean per-channel means in R, G, B order (size 3).
 * @property std per-channel standard deviations in R, G, B order (size 3).
 * @property scaleFactor spatial multiplier of the model output vs. its input.
 *   1 for mask/segmentation models; 4 for the 4x super-resolution upscalers.
 */
data class ModelIoSpec(
    val inputName: String,
    val outputName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val layout: TensorLayout,
    val mean: List<Float>,
    val std: List<Float>,
    val scaleFactor: Int = 1,
)
