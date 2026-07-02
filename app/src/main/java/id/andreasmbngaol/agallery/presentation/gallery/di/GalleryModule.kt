package id.andreasmbngaol.agallery.presentation.gallery.di

import id.andreasmbngaol.agallery.presentation.gallery.GalleryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val galleryModule = module {
    viewModelOf(::GalleryViewModel)
}
