package id.andreasmbngaol.agallery.core.ai

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Tracks whether the XNNPACK execution provider is still safe to use on THIS
 * device, and self-heals from the fact that a bad XNNPACK session build can
 * hard-crash natively (SIGSEGV) inside createSession — an event that CANNOT be
 * caught with try/catch from Kotlin.
 *
 * ## Per-model eligibility (2.4.1)
 * XNNPACK is no longer a single global on/off. Whether a given run may attempt
 * XNNPACK is decided PER MODEL by `AiModelSpec.xnnpackEligible`: only the
 * Conv-dominated Real-ESRGAN upscalers opt in; every other model stays on the
 * plain CPU provider (transformer-heavy SCUNet/GPEN gain nothing, and the
 * background-removal models are blocked by a bilinear `Resize` node that XNNPACK
 * cannot build). This class no longer decides eligibility — it only answers "is
 * XNNPACK still allowed on this device at all?" ([mayUseXnnpack]) and owns the
 * crash guard. Eligible models therefore attempt XNNPACK BY DEFAULT unless this
 * device has permanently disabled it.
 *
 * ## Write-ahead crash guard
 *  1. Right before we ask ONNX Runtime to build an XNNPACK session, we set a
 *     `probePending` flag and flush it to disk SYNCHRONOUSLY (commit()).
 *  2. If the build succeeds, we clear the flag.
 *  3. If it crashes the whole process, step 2 never runs, so the flag survives.
 *     On the next launch [recoverFromCrashIfNeeded] sees the stale flag and
 *     permanently disables XNNPACK for this device, falling back to the plain
 *     CPU provider.
 */
class AccelerationConfig(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether XNNPACK may still be attempted on this device. True by default;
     * flipped to false PERMANENTLY by the crash guard ([recoverFromCrashIfNeeded]
     * / [disableXnnpack]) or by a user hard-off. This is only a DEVICE-level gate:
     * whether any individual run attempts XNNPACK is decided per model by
     * `AiModelSpec.xnnpackEligible` and passed to the engine as `allowXnnpack`.
     */
    fun mayUseXnnpack(): Boolean = !prefs.getBoolean(KEY_DISABLED, false)

    /** Permanently disable XNNPACK on this device (crash guard / user setting). */
    fun disableXnnpack() {
        prefs.edit(commit = true) { putBoolean(KEY_DISABLED, true).putBoolean(KEY_PROBE, false) }
        Log.w(TAG, "XNNPACK disabled for this device")
    }

    /** Write-ahead: mark that an XNNPACK session build is about to happen. */
    fun beginXnnpackProbe() {
        // commit() is synchronous, so the flag is on disk BEFORE the risky native call.
        prefs.edit(commit = true) { putBoolean(KEY_PROBE, true) }
    }

    /** Clears the probe flag after a successful XNNPACK session build. */
    fun endXnnpackProbe() {
        prefs.edit(commit = true) { putBoolean(KEY_PROBE, false) }
    }

    /**
     * Call once on app startup, before any inference. If a probe is still
     * pending, the last XNNPACK attempt crashed the process — disable it.
     */
    fun recoverFromCrashIfNeeded() {
        if (prefs.getBoolean(KEY_PROBE, false)) {
            Log.w(TAG, "Detected a crash during the last XNNPACK session build; disabling XNNPACK")
            disableXnnpack()
        }
    }

    private companion object {
        const val PREFS = "ai_acceleration"
        const val KEY_DISABLED = "xnnpack_disabled"
        const val KEY_PROBE = "xnnpack_probe_pending"
        const val TAG = "AccelerationConfig"
    }
}
