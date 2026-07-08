package id.andreasmbngaol.agallery.core.ai

/**
 * A loaded model ready to run. Sessions own native resources and MUST be closed
 * (they are [AutoCloseable], so prefer `use { }`).
 */
interface InferenceSession : AutoCloseable {
    /** Names of the model's input tensors, in declaration order. */
    val inputNames: List<String>

    /** Names of the model's output tensors, in declaration order. */
    val outputNames: List<String>

    /**
     * Runs the model, feeding [input] as [inputName] and returning the output
     * named [outputName]. Empty names fall back to the first input/output.
     */
    fun run(inputName: String, input: FloatTensor, outputName: String): FloatTensor
}
