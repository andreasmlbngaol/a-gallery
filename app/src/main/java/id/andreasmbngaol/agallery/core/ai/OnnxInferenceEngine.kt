package id.andreasmbngaol.agallery.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * [InferenceEngine] backed by ONNX Runtime (Android), running fully on-device.
 *
 * The session uses the **plain CPU provider** with a multi-threaded intra-op
 * pool sized to the SoC's performance cores (see [pickThreadCount]). This is the
 * reliable fast path for the FP32 segmentation models we run.
 *
 * The **XNNPACK execution provider** is available but **OFF by default**: every
 * one of our models contains a bilinear `Resize` node that XNNPACK rejects at
 * session-build time (`xnn_create_resize_bilinear2d_nhwc_fp32` fails), which
 * aborts the whole session and just wastes time before falling back to CPU. It
 * can be re-enabled per device via [AccelerationConfig] (guarded by a
 * write-ahead crash guard, since a bad XNNPACK build can SIGSEGV uncatchably).
 * NNAPI stays OFF (deprecated in Android 15 and it just falls back to CPU on the
 * budget MediaTek SoCs we target anyway).
 *
 * ## Warm session cache
 * Building a session is expensive (~120-350ms: it reads the .onnx file, runs
 * ONNX graph optimization, and spins up the intra-op thread pool) and this used
 * to happen on EVERY removal/lift because the session was created and closed per
 * call. [acquireSession] keeps the last-used session warm, keyed by model path,
 * so repeated runs on the same model pay the build cost only once (the second
 * run onward skips it entirely). The cache holds a SINGLE session; requesting a
 * different model swaps it once the old one is idle.
 *
 * A warm session pins the model's weights in native RAM (tiny models ~10MB, but
 * IS-Net ~200MB), so the cache is not held forever: it is
 * released once it has been idle for [IDLE_RELEASE_MS], and immediately on memory
 * pressure via [releaseCache] (wired to the Application's onTrimMemory /
 * onLowMemory). A run that is still in flight is never torn out from under
 * itself — releases are deferred until the in-use count returns to zero.
 */
class OnnxInferenceEngine(
    private val accelerationConfig: AccelerationConfig,
) : InferenceEngine {

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    // --- Warm session cache (all fields guarded by [cacheLock]) ---
    private val cacheLock = Any()
    private var cachedPath: String? = null
    private var cachedSession: OrtSession? = null
    private var inUse: Int = 0
    private var releaseWhenIdle: Boolean = false
    private var pendingRelease: ScheduledFuture<*>? = null
    private val releaseScheduler by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "onnx-cache-release").apply { isDaemon = true }
        }
    }

    override fun createSession(modelPath: String): InferenceSession {
        // A one-off, caller-owned session (used to validate a model on import).
        // It is NOT cached and is torn down as soon as the caller closes it.
        val session = buildOrtSession(modelPath)
        return OnnxSession(environment, session) { session.close() }
    }

    override fun acquireSession(modelPath: String): InferenceSession {
        synchronized(cacheLock) {
            pendingRelease?.cancel(false)
            pendingRelease = null
            releaseWhenIdle = false

            val cached = cachedSession
            if (cached != null && cachedPath == modelPath) {
                inUse++
                return OnnxSession(environment, cached) { onCachedHandleClosed() }
            }

            // A different model (or nothing) is cached.
            if (inUse == 0) {
                // Nothing is running: safe to drop the old model and cache the new.
                closeCachedLocked()
                val session = buildOrtSession(modelPath)
                cachedSession = session
                cachedPath = modelPath
                inUse = 1
                return OnnxSession(environment, session) { onCachedHandleClosed() }
            }

            // The cache is busy with another model right now. Build a private,
            // caller-owned session for this run and leave the cache untouched.
            Log.i(TAG, "Cache busy with a different model; building a transient session")
            val session = buildOrtSession(modelPath)
            return OnnxSession(environment, session) { session.close() }
        }
    }

    override fun releaseCache() {
        synchronized(cacheLock) {
            if (inUse > 0) {
                // A run is still in flight; release as soon as it finishes.
                releaseWhenIdle = true
                return
            }
            pendingRelease?.cancel(false)
            pendingRelease = null
            closeCachedLocked()
        }
    }

    /** Called when a cached-session handle is closed (its `use { }` block ends). */
    private fun onCachedHandleClosed() {
        synchronized(cacheLock) {
            if (inUse > 0) inUse--
            if (inUse != 0) return
            if (releaseWhenIdle) {
                releaseWhenIdle = false
                pendingRelease?.cancel(false)
                pendingRelease = null
                closeCachedLocked()
                return
            }
            // Keep the session warm briefly, then let it go if still idle so a big
            // model's weights don't sit in RAM after the user moves on.
            pendingRelease?.cancel(false)
            pendingRelease = releaseScheduler.schedule(
                { releaseCache() },
                IDLE_RELEASE_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun closeCachedLocked() {
        val session = cachedSession ?: return
        val path = cachedPath
        cachedSession = null
        cachedPath = null
        try {
            session.close()
            Log.i(TAG, "Released cached session ($path)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close cached session", t)
        }
    }

    private fun buildOrtSession(modelPath: String): OrtSession {
        val threads = pickThreadCount()

        if (accelerationConfig.isXnnpackEnabled()) {
            try {
                accelerationConfig.beginXnnpackProbe()
                lateinit var session: OrtSession
                val ms = measureTimeMillis {
                    buildOptions(threads, xnnpack = true).use { options ->
                        session = environment.createSession(modelPath, options)
                    }
                }
                accelerationConfig.endXnnpackProbe()
                Log.i(TAG, "Session created with XNNPACK, $threads threads, in $ms ms")
                return session
            } catch (t: Throwable) {
                // Catchable failure (NOT a native SIGSEGV): clear the probe and
                // fall back to the CPU provider for this session.
                accelerationConfig.endXnnpackProbe()
                Log.w(TAG, "XNNPACK session build failed; falling back to CPU", t)
            }
        }

        return try {
            lateinit var s: OrtSession
            val ms = measureTimeMillis {
                buildOptions(threads, xnnpack = false).use { options ->
                    s = environment.createSession(modelPath, options)
                }
            }
            Log.i(TAG, "Session created with CPU provider, $threads threads, in $ms ms")
            s
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create ONNX session for $modelPath", t)
            throw t
        }
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
     * Picks a thread count for the CPU provider, biased toward the SoC's
     * performance cores. It prefers the kernel's per-core `cpu_capacity` — which
     * reflects microarchitecture AND clock, so it cleanly separates "big" from
     * "little" even on modern all-big designs where every core shares a
     * microarchitecture but runs at different clocks (e.g. Dimensity 8400) — and
     * falls back to a max-frequency probe, then to half the logical cores.
     *
     * The result is clamped to 2..6: never single-threaded (a too-tight "big
     * core" test used to pin flagships like the Dimensity 8400 to ONE core and
     * take ~14s for a 1024x1024 run), and never so wide that little cores or
     * thermals cancel the gain.
     */
    private fun pickThreadCount(): Int {
        val logical = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val perf = performanceCoreCount()
        val chosen = if (perf > 0) perf else (logical + 1) / 2
        return chosen.coerceIn(2, 6).coerceAtMost(logical)
    }

    /**
     * Counts the SoC's performance cores. Primary signal is per-core
     * `cpu_capacity` (little cores sit far below the big ones, typically under
     * ~55% of the maximum), which distinguishes clusters even when all cores are
     * the same microarchitecture. Falls back to `cpuinfo_max_freq` with a tight
     * 5% band (which correctly isolates the 2 fast cores on classic big.LITTLE
     * parts like the Helio G99). Returns 0 when neither sysfs node is readable.
     */
    private fun performanceCoreCount(): Int {
        readCoreMetric("cpu_capacity")?.let { caps ->
            val max = caps.max()
            if (max > 0L) return caps.count { it >= max * 0.60 }
        }
        readCoreMetric("cpufreq/cpuinfo_max_freq")?.let { freqs ->
            val max = freqs.max()
            if (max > 0L) return freqs.count { it >= max * 0.95 }
        }
        return 0
    }

    /** Reads a per-core sysfs long for cpu0..cpuN, or null if none are readable. */
    private fun readCoreMetric(relativePath: String): List<Long>? = try {
        val values = ArrayList<Long>()
        var i = 0
        while (true) {
            val f = File("/sys/devices/system/cpu/cpu$i/$relativePath")
            if (!f.exists()) break
            f.readText().trim().toLongOrNull()?.let { values.add(it) }
            i++
        }
        values.ifEmpty { null }
    } catch (t: Throwable) {
        Log.w(TAG, "Unable to probe CPU cores via $relativePath", t)
        null
    }

    /** ONNX Runtime-backed [InferenceSession]. Not exposed outside the engine. */
    private class OnnxSession(
        private val environment: OrtEnvironment,
        private val session: OrtSession,
        private val onClose: () -> Unit,
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

        // Closing a handle does NOT necessarily close the native session: for a
        // cached session this just decrements the in-use count (the engine owns
        // its lifecycle); for a transient session it really closes it.
        override fun close() = onClose()
    }

    private companion object {
        const val TAG = "OnnxInferenceEngine"

        /** How long a cached session stays warm after its last use before release. */
        const val IDLE_RELEASE_MS = 30_000L
    }
}
