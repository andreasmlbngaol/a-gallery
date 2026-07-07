package id.andreasmbngaol.agallery.presentation.tools.qr.di

import id.andreasmbngaol.agallery.data.qr.QrImageSaver
import id.andreasmbngaol.agallery.presentation.tools.qr.QrGeneratorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val qrModule = module {
    single { QrImageSaver(androidContext()) }
    viewModelOf(::QrGeneratorViewModel)
}
