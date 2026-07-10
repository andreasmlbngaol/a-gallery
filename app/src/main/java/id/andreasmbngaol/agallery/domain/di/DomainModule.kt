package id.andreasmbngaol.agallery.domain.di

import id.andreasmbngaol.agallery.domain.usecase.editing.ConvertImageFormatUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RemoveMetadataUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RenameMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.DeleteModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.DetectFacesUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.GetModelCatalogUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ImportModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.RemoveBackgroundUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ResolveModelPathUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.RestoreFacesUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveBackgroundResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveFaceRestoreResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveUpscaleResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.UpscaleImageUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RequestWriteAccessUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ToggleFavoriteUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetMediaPagingUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.ObserveAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.ObserveAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.RefreshMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.SetAlbumCoverUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetComponentStyleUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetEdgeEffectModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetGridColumnsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetLiftModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetLiftQualityUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetPerformanceModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetPinnedAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetSortOrderUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.FinalizePermanentDeleteUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.RestoreFromTrashUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Koin module that wires up every domain use case. Each use case is registered
 * as a factory, so a fresh instance is created for each injection.
 */
val domainModule = module {
    // media
    factoryOf(::GetMediaPagingUseCase)
    factoryOf(::GetAllMediaUseCase)
    factoryOf(::ObserveAllMediaUseCase)
    factoryOf(::GetMediaDetailsUseCase)
    factoryOf(::GetAlbumsUseCase)
    factoryOf(::ObserveAlbumsUseCase)
    factoryOf(::RefreshMediaUseCase)
    factoryOf(::SetAlbumCoverUseCase)

    // editing
    factoryOf(::DeleteMediaUseCase)
    factoryOf(::RenameMediaUseCase)
    factoryOf(::MoveMediaToAlbumUseCase)
    factoryOf(::CopyMediaToAlbumUseCase)
    factoryOf(::RequestWriteAccessUseCase)
    factoryOf(::ConvertImageFormatUseCase)
    factoryOf(::RemoveMetadataUseCase)

    // favorite
    factoryOf(::ToggleFavoriteUseCase)
    factoryOf(::ObserveFavoriteIdsUseCase)

    // trash
    factoryOf(::MoveToTrashUseCase)
    factoryOf(::ObserveTrashItemsUseCase)
    factoryOf(::RestoreFromTrashUseCase)
    factoryOf(::FinalizePermanentDeleteUseCase)

    // ai
    factoryOf(::GetModelCatalogUseCase)
    factoryOf(::ObserveModelStatusUseCase)
    factoryOf(::ImportModelUseCase)
    factoryOf(::DeleteModelUseCase)
    factoryOf(::ResolveModelPathUseCase)
    factoryOf(::RemoveBackgroundUseCase)
    factoryOf(::SaveBackgroundResultUseCase)
    factoryOf(::UpscaleImageUseCase)
    factoryOf(::SaveUpscaleResultUseCase)
    factoryOf(::RestoreFacesUseCase)
    factoryOf(::SaveFaceRestoreResultUseCase)
    factoryOf(::DetectFacesUseCase)

    // settings
    factoryOf(::GetSettingsUseCase)
    factoryOf(::SetComponentStyleUseCase)
    factoryOf(::SetEdgeEffectModeUseCase)
    factoryOf(::SetGridColumnsUseCase)
    factoryOf(::SetPerformanceModeUseCase)
    factoryOf(::SetSortOrderUseCase)
    factoryOf(::SetPinnedAlbumsUseCase)
    factoryOf(::SetLiftModelUseCase)
    factoryOf(::SetLiftQualityUseCase)
}
