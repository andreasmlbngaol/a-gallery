package id.andreasmbngaol.agallery.data.ai.di

import id.andreasmbngaol.agallery.core.ai.AccelerationConfig
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.OnnxInferenceEngine
import id.andreasmbngaol.agallery.data.ai.AiModelRepositoryImpl
import id.andreasmbngaol.agallery.data.ai.BackgroundRemovalProcessor
import id.andreasmbngaol.agallery.data.ai.BackgroundRemovalRepositoryImpl
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.BackgroundRemovalRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for the on-device AI data layer: model storage, the ONNX Runtime
 * inference engine, and the two AI repositories.
 */
val aiModule = module {
    single { ModelPaths(androidContext()) }
    single { DeviceBenchmark(androidContext()) }
    single { AccelerationConfig(androidContext()) }
    single<InferenceEngine> { OnnxInferenceEngine(get()) }
    single { BackgroundRemovalProcessor(androidContext(), get(), get()) }

    single<AiModelRepository> {
        AiModelRepositoryImpl(androidContext(), get(), get())
    }
    single<BackgroundRemovalRepository> {
        BackgroundRemovalRepositoryImpl(androidContext(), get(), get())
    }
}
