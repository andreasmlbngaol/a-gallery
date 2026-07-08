package id.andreasmbngaol.agallery.core.ai

/**
 * Creates [InferenceSession]s from on-disk model files. The concrete engine
 * ([OnnxInferenceEngine]) wraps ONNX Runtime and runs fully on-device.
 */
interface InferenceEngine {
    /**
     * Loads the model at [modelPath] into a runnable session.
     *
     * @throws Exception if the file is missing or is not a loadable model.
     */
    fun createSession(modelPath: String): InferenceSession
}
