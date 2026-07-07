package id.andreasmbngaol.agallery.domain.usecase.editing

import id.andreasmbngaol.agallery.domain.model.conversion.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.conversion.ImageFormat
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Converts a photo to a target format. quality 1..100 applies to lossy formats
 * (JPG/WEBP/HEIC) and is ignored for PNG. Always produces a new file in the same
 * folder; deleting the original (moving it to Trash) is decided by the caller.
 */
class ConvertImageFormatUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(
        uri: String,
        target: ImageFormat,
        quality: Int,
    ): ConversionOutcome = repository.convertImageFormat(uri, target, quality)
}
