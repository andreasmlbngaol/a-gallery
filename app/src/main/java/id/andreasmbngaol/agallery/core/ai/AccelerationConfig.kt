package id.andreasmbngaol.agallery.core.ai

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists whether the XNNPACK execution provider is safe to use on THIS device,
 * and self-heals from the fact that a bad XNNPACK session build can hard-crash
 * natively (SIGSEGV) inside createSession — an event that CANNOT be caught with
 * try/catch from Kotlin.
 *
 * Strategy = "write-ahead crash guard":
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
     * Whether the XNNPACK EP may be used. **OFF by default**: our segmentation
     * models contain a bilinear `Resize` node that XNNPACK cannot build, so it
     * always fails over to CPU after wasting the build time. Can be toggled on
     * per device, and self-disables after a native crash.
     */
    fun isXnnpackEnabled(): Boolean = prefs.getBoolean(KEY_XNNPACK, false)

    /** Permanently disable XNNPACK on this device (crash guard / user setting). */
    fun disableXnnpack() {
        prefs.edit(commit = true) { putBoolean(KEY_XNNPACK, false).putBoolean(KEY_PROBE, false) }
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
        const val KEY_XNNPACK = "xnnpack_enabled"
        const val KEY_PROBE = "xnnpack_probe_pending"
        const val TAG = "AccelerationConfig"
    }
}
