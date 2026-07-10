package id.andreasmbngaol.agallery.data.ai

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.FaceDetectionResult
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreOutcome
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [FaceRestoreRepository] that resolves the installed model via
 * [AiModelRepository], delegates detection + per-face inference to
 * [FaceRestoreProcessor], and saves results into the gallery via MediaStore (a
 * new PNG under Pictures/AGallery Restored; the original is never modified).
 */
class FaceRestoreRepositoryImpl(
    private val context: Context,
    private val modelRepository: AiModelRepository,
    private val processor: FaceRestoreProcessor,
) : FaceRestoreRepository {

    private val resolver get() = context.contentResolver

    override suspend fun detectFaces(sourceUri: String): FaceDetectionResult =
        withContext(Dispatchers.Default) { processor.detect(sourceUri) }

    override suspend fun restore(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): FaceRestoreOutcome {
        val spec = ModelCatalog.byId(modelId)
            ?: return FaceRestoreOutcome.Failure(FaceRestoreOutcome.Reason.NO_MODEL)
        val modelPath = modelRepository.resolvePath(modelId)
            ?: return FaceRestoreOutcome.Failure(FaceRestoreOutcome.Reason.NO_MODEL)

        return withContext(Dispatchers.Default) {
            val source = processor.decodeSource(sourceUri)
                ?: return@withContext FaceRestoreOutcome.Failure(
                    FaceRestoreOutcome.Reason.SOURCE_UNREADABLE,
                )
            try {
                val path = processor.restore(source, spec, modelPath, strength, onProgress)
                FaceRestoreOutcome.Success(path)
            } catch (_: NoFacesException) {
                FaceRestoreOutcome.Failure(FaceRestoreOutcome.Reason.NO_FACES)
            } catch (t: Throwable) {
                Log.e(TAG, "Face restore failed for ${modelId.value}", t)
                FaceRestoreOutcome.Failure(FaceRestoreOutcome.Reason.FAILED)
            } finally {
                source.recycle()
            }
        }
    }

    override suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): FaceRestoreSaveOutcome = withContext(Dispatchers.IO) {
        val file = File(resultPath)
        if (!file.exists()) return@withContext FaceRestoreSaveOutcome.Failure

        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
            .ifBlank { "AGallery" }
        val displayName = "${baseName}_restored_${System.currentTimeMillis()}.png"

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery Restored")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return@withContext FaceRestoreSaveOutcome.Failure
        try {
            file.inputStream().use { input ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                output.use { input.copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            FaceRestoreSaveOutcome.Success(displayName)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            FaceRestoreSaveOutcome.Failure
        }
    }

    private companion object {
        const val TAG = "FaceRestore"
    }
}
