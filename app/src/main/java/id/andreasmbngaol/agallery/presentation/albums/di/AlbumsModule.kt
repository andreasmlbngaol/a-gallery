package id.andreasmbngaol.agallery.presentation.albums.di

import id.andreasmbngaol.agallery.presentation.albums.AlbumsViewModel
import id.andreasmbngaol.agallery.presentation.albums.CreateAlbumViewModel
import id.andreasmbngaol.agallery.presentation.trash.TrashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val albumsModule = module {
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::CreateAlbumViewModel)
    viewModelOf(::TrashViewModel)
}
