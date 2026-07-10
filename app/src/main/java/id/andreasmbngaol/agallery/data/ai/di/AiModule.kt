package id.andreasmbngaol.agallery.data.ai.di

import id.andreasmbngaol.agallery.core.ai.AccelerationConfig
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.core.ai.InferenceEngine
import id.andreasmbngaol.agallery.core.ai.ModelPaths
import id.andreasmbngaol.agallery.core.ai.OnnxInferenceEngine
import id.andreasmbngaol.agallery.data.ai.AiModelRepositoryImpl
import id.andreasmbngaol.agallery.data.ai.BackgroundRemovalProcessor
import id.andreasmbngaol.agallery.data.ai.BackgroundRemovalRepositoryImpl
import id.andreasmbngaol.agallery.data.ai.FaceRestoreProcessor
import id.andreasmbngaol.agallery.data.ai.FaceRestoreRepositoryImpl
import id.andreasmbngaol.agallery.data.ai.ImageUpscaleProcessor
import id.andreasmbngaol.agallery.data.ai.ImageUpscaleRepositoryImpl
import id.andreasmbngaol.agallery.data.ai.PhotoEnhanceProcessor
import id.andreasmbngaol.agallery.data.ai.PhotoEnhanceRepositoryImpl
import id.andreasmbngaol.agallery.domain.repository.AiModelRepository
import id.andreasmbngaol.agallery.domain.repository.BackgroundRemovalRepository
import id.andreasmbngaol.agallery.domain.repository.FaceRestoreRepository
import id.andreasmbngaol.agallery.domain.repository.ImageUpscaleRepository
import id.andreasmbngaol.agallery.domain.repository.PhotoEnhanceRepository
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
    single { ImageUpscaleProcessor(androidContext(), get(), get()) }
    single { FaceRestoreProcessor(androidContext(), get(), get()) }
    single { PhotoEnhanceProcessor(androidContext(), get(), get()) }

    single<AiModelRepository> {
        AiModelRepositoryImpl(androidContext(), get(), get())
    }
    single<BackgroundRemovalRepository> {
        BackgroundRemovalRepositoryImpl(androidContext(), get(), get())
    }
    single<ImageUpscaleRepository> {
        ImageUpscaleRepositoryImpl(androidContext(), get(), get())
    }
    single<FaceRestoreRepository> {
        FaceRestoreRepositoryImpl(androidContext(), get(), get())
    }
    single<PhotoEnhanceRepository> {
        PhotoEnhanceRepositoryImpl(androidContext(), get(), get())
    }
}
