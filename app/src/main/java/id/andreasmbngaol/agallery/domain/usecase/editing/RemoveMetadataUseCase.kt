package id.andreasmbngaol.agallery.domain.usecase.editing

import id.andreasmbngaol.agallery.domain.model.metadata.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataRemovalOutcome
import id.andreasmbngaol.agallery.domain.repository.MediaRepository

/**
 * Strips the selected metadata (categories) from a photo. When saveAsCopy is
 * true the result is saved as a clean copy in the same folder (the original
 * stays intact); when false the original file is overwritten.
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
