package id.andreasmbngaol.agallery.domain.usecase

import id.andreasmbngaol.agallery.domain.model.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.MetadataRemovalOutcome
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Hapus metadata terpilih (categories) dari sebuah foto. Kalau saveAsCopy
 * true, hasil disimpan sebagai salinan bersih di folder yang sama (file asli
 * tetap utuh); kalau false, file asli ditimpa.
 */
class RemoveMetadataUseCase(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(
        uri: String,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ): MetadataRemovalOutcome =
        repository.removeMetadata(uri, categories, saveAsCopy)
}
