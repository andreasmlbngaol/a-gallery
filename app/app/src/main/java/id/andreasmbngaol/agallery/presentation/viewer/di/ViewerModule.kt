package id.andreasmbngaol.agallery.presentation.viewer.di

import id.andreasmbngaol.agallery.presentation.viewer.PhotoViewerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewerModule = module {
    viewModelOf(::PhotoViewerViewModel)
}
