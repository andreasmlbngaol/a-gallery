package id.andreasmbngaol.agallery.domain.model.ai

/**
 * A snapshot of the device's capacity to run on-device models, produced by a
 * small startup benchmark on the AI models screen. It lets the app advise the
 * user, before importing, whether a given model is likely to run comfortably,
 * run slowly, or exceed the device's memory (and be killed by the OS).
 *
 * @property totalRamBytes total physical RAM reported by the OS.
 * @property availRamBytes RAM currently available to allocate.
 * @property isLowRamMemory whether the OS classifies this as a low-RAM device.
 * @property cpuScore relative CPU throughput score where ~1.0 is a mid-range
 *   2020-era phone (higher is faster); 0.0 when the benchmark has not run.
 */
data class DeviceCapability(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val isLowRamMemory: Boolean,
    val cpuScore: Double,
)
