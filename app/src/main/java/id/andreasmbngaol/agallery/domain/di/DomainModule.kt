package id.andreasmbngaol.agallery.domain.di

import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetEdgeEffectModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetGridColumnsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.ToggleFavoriteUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetMediaPagingUseCase(get()) }
    factory { GetMediaDetailsUseCase(get()) }
    factory { DeleteMediaUseCase(get()) }
    factory { GetAlbumsUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { GetSettingsUseCase(get()) }
    factory { SetEdgeEffectModeUseCase(get()) }
    factory { SetGridColumnsUseCase(get()) }
    factory { SetSortOrderUseCase(get()) }
}
