package id.andreasmbngaol.agallery.data.local.mediastore

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads technical media details for the info bottom sheet: dimensions/size from
 * MediaStore, then enriched with EXIF (photos) or video metadata (bitrate,
 * codec, fps, location). Kept separate from [MediaStoreDataSource] because it is
 * a purely standalone read operation.
 */
class MediaDetailsReader(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    suspend fun queryDetails(uriString: String): MediaDetails? = withContext(Dispatchers.IO) {
        val uri = uriString.toUri()
        val proj = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val base = resolver.query(uri, proj, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            MediaDetails(
                sizeBytes = c.getLong(0),
                width = c.getInt(1),
                height = c.getInt(2),
                relativePath = c.getStringOrNull(3).orEmpty(),
            ) to c.getStringOrNull(4).orEmpty()
        } ?: return@withContext null

        val (details, mime) = base
        if (mime.startsWith("video")) enrichVideo(uri, details) else enrichImage(uri, details)
    }

    /** Reads photo EXIF: camera, exposure, flash, capture date & GPS. */
    private fun enrichImage(uri: Uri, base: MediaDetails): MediaDetails = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)

            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            val isoInt = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            val flashRaw = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            val latLong = exif.latLong
            base.copy(
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifEmpty { null },
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifEmpty { null },
                aperture = if (fNumber > 0.0) "f/" + trimDecimal(fNumber) else null,
                shutterSpeed = formatShutter(exposure),
                iso = if (isoInt > 0) isoInt.toString() else null,
                focalLength = if (focal > 0.0) trimDecimal(focal) + " mm" else null,
                flashFired = if (flashRaw < 0) null else (flashRaw and 0x1) == 1,
                dateTakenEpochSeconds = parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
            )
        } ?: base
    } catch (_: Exception) {
        base
    }

    /** Reads technical video metadata: bitrate, dimensions, location, codec & fps. */
    private fun enrichVideo(uri: Uri, base: MediaDetails): MediaDetails {
        var result = base

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { formatBitrate(it) }
            val w = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val (lat, lng) = parseIso6709(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            )
            result = result.copy(
                width = if (result.width > 0) result.width else w,
                height = if (result.height > 0) result.height else h,
                bitrate = bitrate,
                latitude = lat,
                longitude = lng,
            )
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var videoCodec: String? = null
            var audioCodec: String? = null
            var frameRate: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    trackMime.startsWith("video/") && videoCodec == null -> {
                        videoCodec = friendlyCodec(trackMime)
                        frameRate = extractFrameRate(format)
                    }
                    trackMime.startsWith("audio/") && audioCodec == null -> {
                        audioCodec = friendlyCodec(trackMime)
                    }
                }
            }
            result = result.copy(
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                frameRate = frameRate,
            )
        } catch (_: Exception) {
        } finally {
            try { extractor.release() } catch (_: Exception) { }
        }

        return result
    }

    private fun extractFrameRate(format: MediaFormat): String? = try {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            null
        } else {
            val fps = try {
                format.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (_: Exception) {
                format.getFloat(MediaFormat.KEY_FRAME_RATE).toInt()
            }
            if (fps > 0) "$fps fps" else null
        }
    } catch (_: Exception) {
        null
    }

    private fun friendlyCodec(mime: String): String = when (mime.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265 (HEVC)"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/raw" -> "PCM"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        else -> mime.substringAfter('/').uppercase()
    }

    private fun formatBitrate(bps: Long): String =
        if (bps >= 1_000_000L) {
            String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        } else {
            String.format(Locale.US, "%.0f Kbps", bps / 1_000.0)
        }

    private fun trimDecimal(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    private fun formatShutter(seconds: Double): String? = when {
        seconds <= 0.0 -> null
        seconds < 1.0 -> "1/${(1.0 / seconds).roundToInt()} s"
        else -> trimDecimal(seconds) + " s"
    }

    private fun parseExifDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime
                .parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toEpochSecond()
        } catch (_: Exception) {
            null
        }
    }

    /** Parses an ISO-6709 location string (e.g. "+37.42-122.08/") -> lat/long. */
    private fun parseIso6709(raw: String?): Pair<Double?, Double?> {
        if (raw.isNullOrBlank()) return null to null
        return try {
            val cleaned = raw.trim().removeSuffix("/")
            val match = Regex("""([+\-]\d+(?:\.\d+)?)([+\-]\d+(?:\.\d+)?)""")
                .find(cleaned) ?: return null to null
            match.groupValues[1].toDoubleOrNull() to match.groupValues[2].toDoubleOrNull()
        } catch (_: Exception) {
            null to null
        }
    }
}
