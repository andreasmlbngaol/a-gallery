package id.andreasmbngaol.agallery.presentation.viewer.di

import id.andreasmbngaol.agallery.core.qr.QrDetector
import id.andreasmbngaol.agallery.presentation.viewer.PhotoViewerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewerModule = module {
    single { QrDetector(androidContext()) }
    viewModelOf(::PhotoViewerViewModel)
}
