package id.andreasmbngaol.agallery.data.ai

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.BackgroundRemovalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [BackgroundRemovalRepository] that resolves the installed model via
 * [AiModelRepository], delegates inference to [BackgroundRemovalProcessor], and
 * saves results into the gallery via MediaStore (a new PNG under
 * Pictures/AGallery Cutouts; the original is never modified).
 */
class BackgroundRemovalRepositoryImpl(
    private val context: Context,
    private val modelRepository: AiModelRepository,
    private val processor: BackgroundRemovalProcessor,
) : BackgroundRemovalRepository {

    private val resolver get() = context.contentResolver

    override suspend fun removeBackground(
        sourceUri: String,
        modelId: AiModelId,
    ): BackgroundRemovalOutcome {
        val spec = ModelCatalog.byId(modelId)
            ?: return BackgroundRemovalOutcome.Failure(BackgroundRemovalOutcome.Reason.NO_MODEL)
        val modelPath = modelRepository.resolvePath(modelId)
            ?: return BackgroundRemovalOutcome.Failure(BackgroundRemovalOutcome.Reason.NO_MODEL)

        return withContext(Dispatchers.Default) {
            val bitmap = processor.decodeSource(sourceUri)
                ?: return@withContext BackgroundRemovalOutcome.Failure(
                    BackgroundRemovalOutcome.Reason.SOURCE_UNREADABLE,
                )
            try {
                val path = processor.removeBackground(bitmap, spec, modelPath)
                BackgroundRemovalOutcome.Success(path)
            } catch (t: Throwable) {
                Log.e(TAG, "Background removal failed for ${modelId.value}", t)
                BackgroundRemovalOutcome.Failure(BackgroundRemovalOutcome.Reason.FAILED)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val TAG = "BackgroundRemoval"
    }

    override suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): BackgroundSaveOutcome = withContext(Dispatchers.IO) {
        val file = File(resultPath)
        if (!file.exists()) return@withContext BackgroundSaveOutcome.Failure

        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
            .ifBlank { "AGallery" }
        val displayName = "${baseName}_nobg_${System.currentTimeMillis()}.png"

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery Cutouts")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return@withContext BackgroundSaveOutcome.Failure
        try {
            file.inputStream().use { input ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                output.use { input.copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            BackgroundSaveOutcome.Success(displayName)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            BackgroundSaveOutcome.Failure
        }
    }
}
