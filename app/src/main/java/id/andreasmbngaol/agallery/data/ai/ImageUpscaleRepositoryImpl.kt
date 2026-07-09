package id.andreasmbngaol.agallery.data.ai

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [ImageUpscaleRepository] that resolves the installed model via
 * [AiModelRepository], delegates tiled inference to [ImageUpscaleProcessor], and
 * saves results into the gallery via MediaStore (a new PNG under
 * Pictures/AGallery Upscaled; the original is never modified).
 */
class ImageUpscaleRepositoryImpl(
    private val context: Context,
    private val modelRepository: AiModelRepository,
    private val processor: ImageUpscaleProcessor,
) : ImageUpscaleRepository {

    private val resolver get() = context.contentResolver

    override suspend fun upscale(
        sourceUri: String,
        modelId: AiModelId,
        mode: UpscaleMode,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): UpscaleOutcome {
        val spec = ModelCatalog.byId(modelId)
            ?: return UpscaleOutcome.Failure(UpscaleOutcome.Reason.NO_MODEL)
        val modelPath = modelRepository.resolvePath(modelId)
            ?: return UpscaleOutcome.Failure(UpscaleOutcome.Reason.NO_MODEL)

        return withContext(Dispatchers.Default) {
            val decoded = processor.decodeSource(sourceUri, mode)
                ?: return@withContext UpscaleOutcome.Failure(
                    UpscaleOutcome.Reason.SOURCE_UNREADABLE,
                )
            try {
                val path = processor.upscale(
                    decoded.bitmap,
                    spec,
                    modelPath,
                    mode,
                    decoded.originalWidth,
                    decoded.originalHeight,
                    onProgress,
                )
                UpscaleOutcome.Success(path)
            } catch (t: Throwable) {
                Log.e(TAG, "Image upscale failed for ${modelId.value}", t)
                UpscaleOutcome.Failure(UpscaleOutcome.Reason.FAILED)
            } finally {
                decoded.bitmap.recycle()
            }
        }
    }

    override suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): UpscaleSaveOutcome = withContext(Dispatchers.IO) {
        val file = File(resultPath)
        if (!file.exists()) return@withContext UpscaleSaveOutcome.Failure

        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
            .ifBlank { "AGallery" }
        val displayName = "${baseName}_upscaled_${System.currentTimeMillis()}.png"

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery Upscaled")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return@withContext UpscaleSaveOutcome.Failure
        try {
            file.inputStream().use { input ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                output.use { input.copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            UpscaleSaveOutcome.Success(displayName)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            UpscaleSaveOutcome.Failure
        }
    }

    private companion object {
        const val TAG = "ImageUpscale"
    }
}
