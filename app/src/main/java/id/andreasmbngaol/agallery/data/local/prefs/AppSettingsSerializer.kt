package id.andreasmbngaol.agallery.data.local.prefs

import androidx.datastore.core.Serializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer untuk typed DataStore, memakai kotlinx.serialization (JSON).
 * Tanpa protobuf plugin/toolchain — cukup reuse serialization yang sudah ada.
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
            // File korup/kosong -> balik ke default, jangan sampai crash.
            defaultValue
        }

    override suspend fun writeTo(t: AppSettingsDto, output: OutputStream) {
        output.write(
            json.encodeToString(AppSettingsDto.serializer(), t).encodeToByteArray(),
        )
    }
}
