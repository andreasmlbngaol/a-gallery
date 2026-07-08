package id.andreasmbngaol.agallery.core.ai

import android.app.ActivityManager
import android.content.Context
import id.andreasmbngaol.agallery.domain.model.ai.DeviceCapability
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

/**
 * Measures a lightweight snapshot of the device's ability to run on-device
 * models: physical / available RAM (from [ActivityManager]) plus a short CPU
 * micro-benchmark that yields a relative [DeviceCapability.cpuScore]. The whole
 * thing takes a few milliseconds and touches no disk or network, so it is safe
 * to run once when the models screen opens. Call it off the main thread.
 */
class DeviceBenchmark(private val context: Context) {

    /** Prevents the JIT from optimizing the benchmark loop away. */
    private var sink = 0.0

    fun measure(): DeviceCapability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return DeviceCapability(
            totalRamBytes = info.totalMem,
            availRamBytes = info.availMem,
            isLowRamMemory = am.isLowRamDevice,
            cpuScore = cpuScore(),
        )
    }

    /**
     * Runs a fixed floating-point workload and converts elapsed time into a
     * relative score where ~1.0 is a mid-range 2020-era phone (higher = faster).
     * A short warm-up pass lets the runtime settle so the measured pass is
     * stable.
     */
    private fun cpuScore(): Double {
        repeat(WARMUP_PASSES) { sink += busyWork() }
        val nanos = measureNanoTime {
            var acc = 0.0
            repeat(MEASURED_PASSES) { acc += busyWork() }
            sink += acc
        }
        if (nanos <= 0L) return 1.0
        val opsPerSecond =
            (WORKLOAD.toDouble() * MEASURED_PASSES) / (nanos / 1_000_000_000.0)
        return opsPerSecond / REFERENCE_OPS_PER_SECOND
    }

    private fun busyWork(): Double {
        var acc = 0.0
        var x = 1.0
        repeat(WORKLOAD) {
            x += 1.0
            acc += sqrt(x) * 1.0000001
        }
        return acc
    }

    private companion object {
        const val WORKLOAD = 2_000_000
        const val WARMUP_PASSES = 1
        const val MEASURED_PASSES = 3
        // Calibrated so a mid-range device scores ~1.0. Tunable.
        const val REFERENCE_OPS_PER_SECOND = 250_000_000.0
    }
}
