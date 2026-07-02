package id.andreasmbngaol.agallery.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import id.andreasmbngaol.agallery.data.local.mediastore.MediaStoreDataSource
import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsDto
import id.andreasmbngaol.agallery.data.local.prefs.AppSettingsSerializer
import id.andreasmbngaol.agallery.data.local.room.AGalleryDatabase
import id.andreasmbngaol.agallery.data.repository.MediaRepositoryImpl
import id.andreasmbngaol.agallery.data.repository.SettingsRepositoryImpl
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import id.andreasmbngaol.agallery.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AGalleryDatabase::class.java,
            "agallery.db",
        ).build()
    }
    single { get<AGalleryDatabase>().mediaDao() }
    single { MediaStoreDataSource(androidContext()) }

    // Typed DataStore untuk preferensi (kotlinx.serialization / JSON).
    single<DataStore<AppSettingsDto>> {
        DataStoreFactory.create(
            serializer = AppSettingsSerializer,
            produceFile = { androidContext().dataStoreFile("settings.json") },
        )
    }

    singleOf(::MediaRepositoryImpl) bind MediaRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
}
