package id.andreasmbngaol.agallery.core.di

import id.andreasmbngaol.agallery.data.di.dataModule
import id.andreasmbngaol.agallery.domain.di.domainModule
import id.andreasmbngaol.agallery.presentation.albums.di.albumsModule
import id.andreasmbngaol.agallery.presentation.gallery.di.galleryModule
import id.andreasmbngaol.agallery.presentation.settings.di.settingsModule
import id.andreasmbngaol.agallery.presentation.tools.qr.di.qrModule
import id.andreasmbngaol.agallery.presentation.viewer.di.viewerModule

/** Semua Koin module dikumpulkan di sini, dipakai di AGalleryApp. */
val appModules = listOf(
    dataModule,
    domainModule,
    galleryModule,
    viewerModule,
    albumsModule,
    settingsModule,
    qrModule,
)
