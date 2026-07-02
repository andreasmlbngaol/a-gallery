package id.andreasmbngaol.agallery.presentation.settings.di

import id.andreasmbngaol.agallery.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
