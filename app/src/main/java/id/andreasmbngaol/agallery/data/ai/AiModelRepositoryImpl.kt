package id.andreasmbngaol.agallery.data.ai

import android.content.Context
import androidx.core.net.toUri
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ImportOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.model.ai.InstalledModel
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * File-backed [AiModelRepository]. Imported models live in app-private storage
 * (see [ModelPaths]); nothing is ever downloaded. Install state is exposed as a
 * [MutableStateFlow] that re-scans disk after each import/delete.
 */
class AiModelRepositoryImpl(
    private val context: Context,
    private val modelPaths: ModelPaths,
    private val inferenceEngine: InferenceEngine,
) : AiModelRepository {

    private val installedFlow = MutableStateFlow(scanInstalled())

    override fun observeInstalled(): Flow<List<InstalledModel>> = installedFlow.asStateFlow()

    override suspend fun installed(id: AiModelId): InstalledModel? =
        withContext(Dispatchers.IO) { readInstalled(id) }

    override suspend fun resolvePath(id: AiModelId): String? = withContext(Dispatchers.IO) {
        val file = modelPaths.modelFile(id)
        if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    override suspend fun import(
        spec: AiModelSpec,
        sourceUri: String,
        onPhase: (ImportPhase) -> Unit,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val target = modelPaths.modelFile(spec.id)
        val temp = File(modelPaths.modelsDir(), "${spec.id.value}.import")

        // 1) Copy the picked file into private storage.
        onPhase(ImportPhase.COPYING)
        try {
            val opened = context.contentResolver.openInputStream(sourceUri.toUri())
            if (opened == null) {
                temp.delete()
                return@withContext ImportOutcome.Failure(ImportOutcome.Reason.COPY_FAILED)
            }
            opened.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        } catch (_: Throwable) {
            temp.delete()
            return@withContext ImportOutcome.Failure(ImportOutcome.Reason.COPY_FAILED)
        }

        // 2) Verify size / checksum when the spec declares them.
        onPhase(ImportPhase.VERIFYING)
        if (spec.expectedSizeBytes > 0 && temp.length() != spec.expectedSizeBytes) {
            temp.delete()
            return@withContext ImportOutcome.Failure(ImportOutcome.Reason.SIZE_MISMATCH)
        }
        if (spec.sha256.isNotBlank()) {
            val actual = sha256Of(temp)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                temp.delete()
                return@withContext ImportOutcome.Failure(ImportOutcome.Reason.CHECKSUM_MISMATCH)
            }
        }

        // 3) Validate it is actually a loadable ONNX model for inference.
        try {
            inferenceEngine.createSession(temp.absolutePath, allowXnnpack = false).use { /* loads = valid */ }
        } catch (t: Throwable) {
            temp.delete()
            val message = t.message?.lowercase().orEmpty()
            val reason = if (
                "protobuf" in message ||
                "load model" in message ||
                "parse" in message ||
                "invalid" in message ||
                "corrupt" in message
            ) {
                ImportOutcome.Reason.INVALID_MODEL
            } else {
                ImportOutcome.Reason.INFERENCE_FAILED
            }
            return@withContext ImportOutcome.Failure(reason)
        }

        // 4) Commit into place.
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            try {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            } catch (_: Throwable) {
                temp.delete()
                return@withContext ImportOutcome.Failure(ImportOutcome.Reason.COPY_FAILED)
            }
        }

        val model = InstalledModel(
            id = spec.id,
            absolutePath = target.absolutePath,
            sizeBytes = target.length(),
            importedAtEpochMs = System.currentTimeMillis(),
        )
        installedFlow.value = scanInstalled()
        ImportOutcome.Success(model)
    }

    override suspend fun delete(id: AiModelId): Boolean = withContext(Dispatchers.IO) {
        val file = modelPaths.modelFile(id)
        val removed = file.exists() && file.delete()
        installedFlow.value = scanInstalled()
        removed
    }

    private fun readInstalled(id: AiModelId): InstalledModel? {
        val file = modelPaths.modelFile(id)
        return if (file.exists() && file.length() > 0) {
            InstalledModel(
                id = id,
                absolutePath = file.absolutePath,
                sizeBytes = file.length(),
                importedAtEpochMs = file.lastModified(),
            )
        } else {
            null
        }
    }

    private fun scanInstalled(): List<InstalledModel> {
        val files = modelPaths.modelsDir().listFiles().orEmpty()
        return files
            .filter { it.isFile && it.extension == "onnx" && it.length() > 0 }
            .map { file ->
                InstalledModel(
                    id = AiModelId(file.nameWithoutExtension),
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    importedAtEpochMs = file.lastModified(),
                )
            }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
