package id.andreasmbngaol.agallery.data.ai

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.PhotoEnhanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [PhotoEnhanceRepository] that resolves the installed model via
 * [AiModelRepository], delegates tiled inference to [PhotoEnhanceProcessor], and
 * saves results into the gallery via MediaStore (a new PNG under
 * Pictures/AGallery Enhanced; the original is never modified).
 */
class PhotoEnhanceRepositoryImpl(
    private val context: Context,
    private val modelRepository: AiModelRepository,
    private val processor: PhotoEnhanceProcessor,
) : PhotoEnhanceRepository {

    private val resolver get() = context.contentResolver

    override suspend fun enhance(
        sourceUri: String,
        modelId: AiModelId,
        strength: Float,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): EnhanceOutcome {
        val spec = ModelCatalog.byId(modelId)
            ?: return EnhanceOutcome.Failure(EnhanceOutcome.Reason.NO_MODEL)
        val modelPath = modelRepository.resolvePath(modelId)
            ?: return EnhanceOutcome.Failure(EnhanceOutcome.Reason.NO_MODEL)

        return withContext(Dispatchers.Default) {
            val decoded = processor.decodeSource(sourceUri)
                ?: return@withContext EnhanceOutcome.Failure(
                    EnhanceOutcome.Reason.SOURCE_UNREADABLE,
                )
            try {
                val path = processor.enhance(
                    decoded.bitmap,
                    spec,
                    modelPath,
                    strength,
                    decoded.originalWidth,
                    decoded.originalHeight,
                    onProgress,
                )
                EnhanceOutcome.Success(path)
            } catch (t: Throwable) {
                Log.e(TAG, "Photo enhance failed for ${modelId.value}", t)
                EnhanceOutcome.Failure(EnhanceOutcome.Reason.FAILED)
            } finally {
                decoded.bitmap.recycle()
            }
        }
    }

    override suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): EnhanceSaveOutcome = withContext(Dispatchers.IO) {
        val file = File(resultPath)
        if (!file.exists()) return@withContext EnhanceSaveOutcome.Failure

        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
            .ifBlank { "AGallery" }
        val displayName = "${baseName}_enhanced_${System.currentTimeMillis()}.png"

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery Enhanced")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return@withContext EnhanceSaveOutcome.Failure
        try {
            file.inputStream().use { input ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                output.use { input.copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            EnhanceSaveOutcome.Success(displayName)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            EnhanceSaveOutcome.Failure
        }
    }

    private companion object {
        const val TAG = "PhotoEnhance"
    }
}
