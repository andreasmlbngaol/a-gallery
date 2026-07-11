package id.andreasmbngaol.agallery.presentation.ai.di

import id.andreasmbngaol.agallery.presentation.ai.AiModelsViewModel
import id.andreasmbngaol.agallery.presentation.ai.AutoEnhanceViewModel
import id.andreasmbngaol.agallery.presentation.ai.BackgroundRemoverViewModel
import id.andreasmbngaol.agallery.presentation.ai.FaceRestoreViewModel
import id.andreasmbngaol.agallery.presentation.ai.ImageUpscaleViewModel
import id.andreasmbngaol.agallery.presentation.ai.PhotoEnhanceViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the AI presentation layer.
 *
 * [BackgroundRemoverViewModel] takes the source image uri and display name as
 * runtime parameters (from the navigation route), so it is declared with an
 * explicit `viewModel { (uri, name) -> ... }` factory rather than [viewModelOf].
 */
val aiPresentationModule = module {
    viewModelOf(::AiModelsViewModel)
    viewModel { (sourceUri: String, sourceDisplayName: String) ->
        BackgroundRemoverViewModel(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            observeModelStatus = get(),
            getSettings = get(),
            removeBackground = get(),
            saveResult = get(),
            deviceBenchmark = get(),
        )
    }
    viewModel { (sourceUri: String, sourceDisplayName: String) ->
        ImageUpscaleViewModel(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            observeModelStatus = get(),
            getSettings = get(),
            upscaleImage = get(),
            saveResult = get(),
            deviceBenchmark = get(),
        )
    }
    viewModel { (sourceUri: String, sourceDisplayName: String) ->
        FaceRestoreViewModel(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            observeModelStatus = get(),
            getSettings = get(),
            detectFacesUseCase = get(),
            restoreFaces = get(),
            saveResult = get(),
            deviceBenchmark = get(),
        )
    }
    viewModel { (sourceUri: String, sourceDisplayName: String) ->
        PhotoEnhanceViewModel(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            observeModelStatus = get(),
            getSettings = get(),
            enhancePhoto = get(),
            saveResult = get(),
            deviceBenchmark = get(),
        )
    }
    viewModel { (sourceUri: String, sourceDisplayName: String) ->
        AutoEnhanceViewModel(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            observeModelStatus = get(),
            getSettings = get(),
            autoEnhance = get(),
            saveResult = get(),
            deviceBenchmark = get(),
        )
    }
}
