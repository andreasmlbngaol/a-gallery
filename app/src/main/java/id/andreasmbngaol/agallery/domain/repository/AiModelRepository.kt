package id.andreasmbngaol.agallery.domain.repository

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ImportOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.model.ai.InstalledModel
import kotlinx.coroutines.flow.Flow

/**
 * Contract for storing and validating user-imported AI model files. The
 * implementation (app-private storage + ONNX Runtime) lives in the data layer.
 *
 * Models are never downloaded by the app: [import] copies a file the user picked
 * via the system file picker into app storage, and only network-free, on-device
 * operations are performed.
 */
interface AiModelRepository {
    /** Streams the set of currently installed models, updating as files change. */
    fun observeInstalled(): Flow<List<InstalledModel>>

    /** Returns the installed model for [id], or null if it is not installed. */
    suspend fun installed(id: AiModelId): InstalledModel?

    /** Resolves the absolute `.onnx` path for [id], or null if not installed. */
    suspend fun resolvePath(id: AiModelId): String?

    /**
     * Copies and validates the file at [sourceUri] as the model described by
     * [spec]. [onPhase] reports progress. A rejected import leaves no partial
     * file behind.
     */
    suspend fun import(
        spec: AiModelSpec,
        sourceUri: String,
        onPhase: (ImportPhase) -> Unit,
    ): ImportOutcome

    /** Deletes the installed model file for [id]; returns true if a file was removed. */
    suspend fun delete(id: AiModelId): Boolean
}
