package id.andreasmbngaol.agallery.domain.di

import id.andreasmbngaol.agallery.domain.usecase.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RefreshMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetAlbumCoverUseCase
import id.andreasmbngaol.agallery.domain.usecase.RestoreFromTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.FinalizePermanentDeleteUseCase
import id.andreasmbngaol.agallery.domain.usecase.ConvertImageFormatUseCase
import id.andreasmbngaol.agallery.domain.usecase.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RemoveMetadataUseCase
import id.andreasmbngaol.agallery.domain.usecase.RenameMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.RequestWriteAccessUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetComponentStyleUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetEdgeEffectModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetGridColumnsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetPerformanceModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetPinnedAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.ToggleFavoriteUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetMediaPagingUseCase(get()) }
    factory { GetAllMediaUseCase(get()) }
    factory { GetMediaDetailsUseCase(get()) }
    factory { DeleteMediaUseCase(get()) }
    factory { GetAlbumsUseCase(get()) }
    factory { ObserveAlbumsUseCase(get()) }
    factory { SetAlbumCoverUseCase(get()) }
    factory { RefreshMediaUseCase(get()) }
    factory { RequestWriteAccessUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { MoveToTrashUseCase(get()) }
    factory { ObserveTrashItemsUseCase(get()) }
    factory { RestoreFromTrashUseCase(get()) }
    factory { FinalizePermanentDeleteUseCase(get()) }
    factory { ObserveFavoriteIdsUseCase(get()) }
    factory { RenameMediaUseCase(get()) }
    factory { RemoveMetadataUseCase(get()) }
    factory { ConvertImageFormatUseCase(get()) }
    factory { MoveMediaToAlbumUseCase(get()) }
    factory { CopyMediaToAlbumUseCase(get()) }
    factory { GetSettingsUseCase(get()) }
    factory { SetEdgeEffectModeUseCase(get()) }
    factory { SetComponentStyleUseCase(get()) }
    factory { SetGridColumnsUseCase(get()) }
    factory { SetPerformanceModeUseCase(get()) }
    factory { SetSortOrderUseCase(get()) }
    factory { SetPinnedAlbumsUseCase(get()) }
}
