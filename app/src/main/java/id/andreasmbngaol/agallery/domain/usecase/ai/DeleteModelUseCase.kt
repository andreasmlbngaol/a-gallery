package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository

/** Deletes an installed model file; returns true if a file was removed. */
class DeleteModelUseCase(
    private val repository: AiModelRepository,
) {
    suspend operator fun invoke(id: AiModelId): Boolean = repository.delete(id)
}
