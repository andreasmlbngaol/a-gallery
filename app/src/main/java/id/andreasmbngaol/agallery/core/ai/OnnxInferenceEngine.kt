package id.andreasmbngaol.agallery.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import kotlin.system.measureTimeMillis

/**
 * [InferenceEngine] backed by ONNX Runtime (Android), running fully on-device.
 *
 * By default the session now uses the **XNNPACK execution provider** (highly
 * optimized ARM NEON microkernels + its own threadpool), which is meaningfully
 * faster than the plain CPU provider for the FP32 segmentation models we run.
 *
 * XNNPACK can, on some models/devices, hard-crash natively (SIGSEGV) inside
 * createSession — an event that CANNOT be caught from Kotlin. [AccelerationConfig]
 * wraps each XNNPACK build in a write-ahead crash guard, so if that ever happens
 * the app permanently and automatically falls back to the plain CPU provider on
 * the next launch. NNAPI stays OFF (deprecated in Android 15 and it just falls
 * back to CPU on the budget MediaTek SoCs we target anyway).
 */
class OnnxInferenceEngine(
    private val accelerationConfig: AccelerationConfig,
) : InferenceEngine {

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    override fun createSession(modelPath: String): InferenceSession {
        val threads = pickThreadCount()

        if (accelerationConfig.isXnnpackEnabled()) {
            try {
                accelerationConfig.beginXnnpackProbe()
                lateinit var session: OrtSession
                val ms = measureTimeMillis {
                    session = environment.createSession(modelPath, buildOptions(threads, xnnpack = true))
                }
                accelerationConfig.endXnnpackProbe()
                Log.i(TAG, "Session created with XNNPACK, $threads threads, in $ms ms")
                return OnnxSession(environment, session)
            } catch (t: Throwable) {
                // Catchable failure (NOT a native SIGSEGV): clear the probe and
                // fall back to the CPU provider for this session.
                accelerationConfig.endXnnpackProbe()
                Log.w(TAG, "XNNPACK session build failed; falling back to CPU", t)
            }
        }

        val session = try {
            lateinit var s: OrtSession
            val ms = measureTimeMillis {
                s = environment.createSession(modelPath, buildOptions(threads, xnnpack = false))
            }
            Log.i(TAG, "Session created with CPU provider, $threads threads, in $ms ms")
            s
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create ONNX session for $modelPath", t)
            throw t
        }
        return OnnxSession(environment, session)
    }

    private fun buildOptions(threads: Int, xnnpack: Boolean): OrtSession.SessionOptions =
        OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            try {
                if (xnnpack) {
                    // XNNPACK has its OWN pthread pool. Keep ORT's intra-op pool at
                    // 1 and disable spinning so the two pools don't contend for CPU
                    // (that contention was cancelling out the XNNPACK speedup).
                    setIntraOpNumThreads(1)
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                } else {
                    setIntraOpNumThreads(threads)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to set thread options", t)
            }
            // Lower peak native RAM so heavy 1024x1024 models are more likely to
            // finish on low-RAM devices.
            try {
                setMemoryPatternOptimization(false)
                setCPUArenaAllocator(false)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to apply low-memory session options", t)
            }
            if (xnnpack) {
                // Give the real (big-core) thread count to XNNPACK's own pool.
                addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            }
        }

    /**
     * Picks a thread count biased toward the SoC's "big" cores. On a big.LITTLE
     * chip (e.g. Helio G99 = 2xA76 + 6xA55) running only on the fast cores is
     * usually quicker and cooler than spreading across the slow cores. Falls back
     * to a capped logical-core count if the sysfs probe fails (e.g. Helio G35,
     * which is 8x identical A53 cores -> capped at 4).
     */
    private fun pickThreadCount(): Int {
        val big = bigCoreCount()
        val logical = Runtime.getRuntime().availableProcessors()
        return if (big in 1..logical) big else logical.coerceIn(1, 4)
    }

    private fun bigCoreCount(): Int = try {
        val freqs = ArrayList<Long>()
        var i = 0
        while (true) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (!f.exists()) break
            f.readText().trim().toLongOrNull()?.let { freqs.add(it) }
            i++
        }
        if (freqs.isEmpty()) {
            0
        } else {
            val max = freqs.max()
            // Cores whose max freq is within 5% of the fastest = the "big" cluster.
            freqs.count { it >= max * 0.95 }.coerceIn(1, 4)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Unable to probe CPU cores", t)
        0
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
                lateinit var out: FloatTensor
                val ms = measureTimeMillis {
                    session.run(mapOf(resolvedInput to tensor), setOf(resolvedOutput)).use { result ->
                        val output = result.get(resolvedOutput).orElseGet { result.get(0) }
                        val onnx = output as OnnxTensor
                        val shape = (onnx.info as TensorInfo).shape
                        val floats = onnx.floatBuffer
                            ?: error("Model output is not a float tensor")
                        val arr = FloatArray(floats.remaining())
                        floats.get(arr)
                        out = FloatTensor(arr, shape)
                    }
                }
                Log.i(TAG, "Inference run took $ms ms")
                return out
            }
        }

        override fun close() = session.close()
    }

    private companion object {
        const val TAG = "OnnxInferenceEngine"
    }
}
