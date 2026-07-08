package id.andreasmbngaol.agallery.core.ai

/**
 * A dense float tensor: a flat [data] buffer interpreted by [shape]. Used as the
 * transport type between [TensorImageUtils], [InferenceSession], and the
 * background-removal processor so no engine-specific types leak out of
 * `core/ai`.
 */
data class FloatTensor(
    val data: FloatArray,
    val shape: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FloatTensor) return false
        return data.contentEquals(other.data) && shape.contentEquals(other.shape)
    }

    override fun hashCode(): Int = 31 * data.contentHashCode() + shape.contentHashCode()
}
