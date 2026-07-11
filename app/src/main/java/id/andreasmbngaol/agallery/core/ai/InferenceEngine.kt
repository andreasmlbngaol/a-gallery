package id.andreasmbngaol.agallery.core.ai

/**
 * Creates [InferenceSession]s from on-disk model files. The concrete engine
 * ([OnnxInferenceEngine]) wraps ONNX Runtime and runs fully on-device.
 */
interface InferenceEngine {
    /**
     * Loads the model at [modelPath] into a runnable, caller-owned session. The
     * caller MUST close it (prefer `use { }`). Use this for one-off work such as
     * validating a freshly imported model; for repeated inference prefer
     * [acquireSession], which reuses a warm session.
     *
     * @param allowXnnpack whether this model may attempt the XNNPACK execution
     *   provider. Only the Conv-dominated Real-ESRGAN upscalers opt in (see
     *   `AiModelSpec.xnnpackEligible`); import validation always passes false.
     * @throws Exception if the file is missing or is not a loadable model.
     */
    fun createSession(modelPath: String, allowXnnpack: Boolean): InferenceSession

    /**
     * Returns a runnable session for the model at [modelPath], backed by an
     * engine-managed warm cache keyed by that path. The first call builds the
     * session (hundreds of ms); later calls for the SAME model reuse it with no
     * build cost. Closing the returned session does NOT tear the model down
     * immediately — the engine keeps it warm and releases it once idle (or on
     * [releaseCache]). Still call close()/`use { }`: it hands the session back to
     * the cache. Requesting a different model swaps the cached one when idle.
     *
     * @param allowXnnpack whether this model may attempt the XNNPACK execution
     *   provider. Only the Conv-dominated Real-ESRGAN upscalers opt in (see
     *   `AiModelSpec.xnnpackEligible`); everything else passes false to stay on
     *   the CPU provider.
     * @throws Exception if the file is missing or is not a loadable model.
     */
    fun acquireSession(modelPath: String, allowXnnpack: Boolean): InferenceSession

    /**
     * Immediately releases the cached warm session to reclaim its native RAM,
     * unless a run is currently in flight (in which case the release happens as
     * soon as that run finishes). Safe to call at any time; a no-op if nothing
     * is cached. Typically wired to the Application's onTrimMemory/onLowMemory.
     */
    fun releaseCache()
}
