package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Streams the catalog for a [AiFeature] joined with each model's install state,
 * so the model list reactively reflects imports and deletions.
 */
class ObserveModelStatusUseCase(
    private val repository: AiModelRepository,
) {
    operator fun invoke(feature: AiFeature): Flow<List<ModelStatus>> =
        repository.observeInstalled().map { installed ->
            val byId = installed.associateBy { it.id }
            ModelCatalog.forFeature(feature).map { spec ->
                ModelStatus(spec = spec, installed = byId[spec.id])
            }
        }
}
