package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository

/** Resolves the absolute path of an installed model, or null if not installed. */
class ResolveModelPathUseCase(
    private val repository: AiModelRepository,
) {
    suspend operator fun invoke(id: AiModelId): String? = repository.resolvePath(id)
}
