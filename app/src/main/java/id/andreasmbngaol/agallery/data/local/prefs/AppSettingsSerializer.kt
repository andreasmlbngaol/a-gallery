package id.andreasmbngaol.agallery.data.local.prefs

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for the typed DataStore, using kotlinx.serialization (JSON).
 *
 * Avoids the protobuf plugin/toolchain by reusing the serialization support the
 * app already depends on.
 */
object AppSettingsSerializer : Serializer<AppSettingsDto> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: AppSettingsDto = AppSettingsDto()

    override suspend fun readFrom(input: InputStream): AppSettingsDto =
        try {
            json.decodeFromString(
                AppSettingsDto.serializer(),
                input.readBytes().decodeToString(),
            )
        } catch (_: Exception) {
            defaultValue
        }

    override suspend fun writeTo(t: AppSettingsDto, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                json.encodeToString(AppSettingsDto.serializer(), t).encodeToByteArray(),
            )
        }
    }
}
