package id.andreasmbngaol.agallery.domain.usecase.ai

import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog

/** Returns the built-in catalog of models that power the given [AiFeature]. */
class GetModelCatalogUseCase {
    operator fun invoke(feature: AiFeature): List<AiModelSpec> =
        ModelCatalog.forFeature(feature)
}
