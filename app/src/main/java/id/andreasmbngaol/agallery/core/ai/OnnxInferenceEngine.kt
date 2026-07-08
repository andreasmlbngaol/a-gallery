package id.andreasmbngaol.agallery.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.nio.FloatBuffer

/**
 * [InferenceEngine] backed by ONNX Runtime (Android), running fully on-device.
 *
 * Sessions run on ONNX Runtime's built-in CPU provider. NNAPI is deliberately
 * NOT used (deprecated, inconsistent across vendor drivers for segmentation
 * models), and the XNNPACK provider is intentionally left OFF: it was observed
 * to hard-crash (native SIGSEGV, uncatchable from Kotlin) inside createSession
 * for some segmentation models on some devices, taking the whole app down. The
 * plain CPU provider is a little slower but reliable, and — crucially — surfaces
 * invalid/corrupt model files as catchable exceptions instead of crashing.
 */
class OnnxInferenceEngine : InferenceEngine {

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    override fun createSession(modelPath: String): InferenceSession {
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            try {
                setIntraOpNumThreads(threads)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to set intra-op threads", t)
            }
            // Heavy segmentation models (e.g. BiRefNet at 1024x1024) can exhaust
            // native RAM and either OOM or thrash for a long time before failing.
            // Trading a little speed for a lower peak footprint makes them far
            // more likely to actually finish on-device.
            try {
                setMemoryPatternOptimization(false)
                setCPUArenaAllocator(false)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to apply low-memory session options", t)
            }
            // No XNNPACK / NNAPI: run on the default CPU provider only. See the
            // class KDoc for why the XNNPACK EP is intentionally disabled.
        }
        val session = try {
            environment.createSession(modelPath, options)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create ONNX session for $modelPath", t)
            throw t
        }
        return OnnxSession(environment, session)
    }

    /** ONNX Runtime-backed [InferenceSession]. Not exposed outside the engine. */
    private class OnnxSession(
        private val environment: OrtEnvironment,
        private val session: OrtSession,
    ) : InferenceSession {

        override val inputNames: List<String> get() = session.inputNames.toList()
        override val outputNames: List<String> get() = session.outputNames.toList()

        override fun run(
            inputName: String,
            input: FloatTensor,
            outputName: String,
        ): FloatTensor {
            val resolvedInput = inputName.ifEmpty { session.inputNames.first() }
            val resolvedOutput = outputName.ifEmpty { session.outputNames.first() }
            val buffer = FloatBuffer.wrap(input.data)
            OnnxTensor.createTensor(environment, buffer, input.shape).use { tensor ->
                session.run(mapOf(resolvedInput to tensor), setOf(resolvedOutput)).use { result ->
                    val output = result.get(resolvedOutput).orElseGet { result.get(0) }
                    val onnx = output as OnnxTensor
                    val shape = (onnx.info as TensorInfo).shape
                    val floats = onnx.floatBuffer
                        ?: error("Model output is not a float tensor")
                    val out = FloatArray(floats.remaining())
                    floats.get(out)
                    return FloatTensor(out, shape)
                }
            }
        }

        override fun close() = session.close()
    }

    private companion object {
        const val TAG = "OnnxInferenceEngine"
    }
}
