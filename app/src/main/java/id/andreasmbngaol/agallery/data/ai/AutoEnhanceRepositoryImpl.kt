package id.andreasmbngaol.agallery.data.ai

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.repository.AutoEnhanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [AutoEnhanceRepository] that saves an Auto Enhance preview into the gallery
 * via MediaStore (a new PNG under Pictures/AGallery Auto Enhanced; the original
 * is never modified). The pipeline itself lives in `AutoEnhanceUseCase`; this
 * only owns the final save so the result lands in its own dedicated folder.
 */
class AutoEnhanceRepositoryImpl(
    private val context: Context,
) : AutoEnhanceRepository {

    private val resolver get() = context.contentResolver

    override suspend fun saveResult(
        resultPath: String,
        sourceDisplayName: String,
    ): AutoEnhanceSaveOutcome = withContext(Dispatchers.IO) {
        val file = File(resultPath)
        if (!file.exists()) return@withContext AutoEnhanceSaveOutcome.Failure

        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
            .ifBlank { "AGallery" }
        val displayName = "${baseName}_auto_enhanced_${System.currentTimeMillis()}.png"

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AGallery Auto Enhanced")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return@withContext AutoEnhanceSaveOutcome.Failure
        try {
            file.inputStream().use { input ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                output.use { input.copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            AutoEnhanceSaveOutcome.Success(displayName)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            AutoEnhanceSaveOutcome.Failure
        }
    }
}
