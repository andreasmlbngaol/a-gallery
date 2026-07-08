package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Describes how images are fed into a model and how its single-channel mask is
 * read back. Every value is intrinsic to a given `.onnx` export and is declared
 * up-front in the [ModelCatalog] — it is never guessed at runtime.
 *
 * Normalization is applied per channel as `(pixel / 255 - mean) / std`.
 *
 * @property inputName name of the image input tensor, or empty to fall back to
 *   the session's first input.
 * @property outputName name of the mask output tensor, or empty to fall back to
 *   the session's first output.
 * @property inputWidth expected input width, in pixels.
 * @property inputHeight expected input height, in pixels.
 * @property layout channel ordering of the input tensor.
 * @property mean per-channel means in R, G, B order (size 3).
 * @property std per-channel standard deviations in R, G, B order (size 3).
 */
data class ModelIoSpec(
    val inputName: String,
    val outputName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val layout: TensorLayout,
    val mean: List<Float>,
    val std: List<Float>,
)
