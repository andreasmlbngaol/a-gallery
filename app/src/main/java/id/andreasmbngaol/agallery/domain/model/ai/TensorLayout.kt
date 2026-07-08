package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Memory layout of a 4-D image tensor, i.e. how pixels are packed into a model's
 * input/output buffers.
 *
 * - [NCHW]: batch, channel, height, width (channel-first). Every catalog model
 *   uses this layout.
 * - [NHWC]: batch, height, width, channel (channel-last).
 */
enum class TensorLayout {
    NCHW,
    NHWC,
}
