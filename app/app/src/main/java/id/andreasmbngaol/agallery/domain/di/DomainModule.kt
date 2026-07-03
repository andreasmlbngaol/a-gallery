package id.andreasmbngaol.agallery.domain.di

import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RenameMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetComponentStyleUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetEdgeEffectModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetGridColumnsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetPerformanceModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.ToggleFavoriteUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetMediaPagingUseCase(get()) }
    factory { GetAllMediaUseCase(get()) }
    factory { GetMediaDetailsUseCase(get()) }
    factory { DeleteMediaUseCase(get()) }
    factory { GetAlbumsUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { MoveToTrashUseCase(get()) }
    factory { ObserveFavoriteIdsUseCase(get()) }
    factory { RenameMediaUseCase(get()) }
    factory { MoveMediaToAlbumUseCase(get()) }
    factory { CopyMediaToAlbumUseCase(get()) }
    factory { GetSettingsUseCase(get()) }
    factory { SetEdgeEffectModeUseCase(get()) }
    factory { SetComponentStyleUseCase(get()) }
    factory { SetGridColumnsUseCase(get()) }
    factory { SetPerformanceModeUseCase(get()) }
    factory { SetSortOrderUseCase(get()) }
}
