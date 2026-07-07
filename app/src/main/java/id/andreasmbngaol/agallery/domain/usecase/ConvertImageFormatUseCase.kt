package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.ImageFormat
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Konversi sebuah foto ke target format. quality 1..100 dipakai untuk format
 * lossy (JPG/WEBP/HEIC); diabaikan untuk PNG. Selalu menghasilkan file baru di
 * folder yang sama; menghapus asli (pindah Trash) diputuskan di pemanggil.
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
