import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Kredensial signing dibaca dari keystore.properties (lokal) ATAU environment variable (CI).
// Kalau dua-duanya nggak ada, release build fallback ke debug signing biar build lokal nggak error.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// Ambil nilai dari keystore.properties dulu, baru fallback ke env var (dipakai di GitHub Actions).
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

android {
    namespace = "id.andreasmbngaol.agallery"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "id.andreasmbngaol.agallery"
        minSdk = 29
        targetSdk = 37
        versionCode = 6
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // storeFile cuma di-set kalau path-nya tersedia (lokal atau CI).
            val storeFilePath = signingValue("storeFile", "KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Pakai release signing kalau keystore tersedia; kalau nggak, fallback ke debug.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }

            // R8: shrink + obfuscate kode yang nggak kepakai, plus buang resource
            // yang nggak dipakai. Ini yang bikin APK jauh lebih kecil & enteng.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3 ExoPlayer — pemutar video di viewer (autoplay + kontrol custom).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Liquid glass (Kyant backdrop) untuk floating nav bar. Efek lens butuh API 33+.
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    // implementation(libs.coil.network.okhttp) // aktifkan kalau perlu load dari URL

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ViewModel + lifecycle Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Permission
    implementation(libs.accompanist.permissions)

    // Pinch-to-zoom
    implementation(libs.telephoto.zoomable.coil)

    // Room (cache metadata)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Koin (DI) — pakai BOM biar versi modul sinkron
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Splash screen
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.haze)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.phosphor.icon)

    // Baseline Profile installer — masang baseline profile (kalau ada) pas app
    // dipasang, biar startup & scroll lebih ngebut (ART AOT-compile jalur panas).
    implementation(libs.androidx.profileinstaller)


    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
