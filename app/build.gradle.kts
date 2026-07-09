import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Signing credentials come from keystore.properties (local) or environment
// variables (CI). If neither is present, the release build falls back to debug
// signing so local builds still succeed.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// Prefer keystore.properties, then fall back to env vars (used by GitHub Actions).
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
        versionCode = 22
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // storeFile is only set when a path is available (local or CI).
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
            // Use release signing when a keystore is available; otherwise fall back to debug.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }

            // R8: shrink and obfuscate unused code and strip unused resources,
            // which keeps the APK small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // ABI splits: emit one APK per architecture so each file only carries the
    // native libs (.so) for its own ABI. ML Kit (bundled) ships large per-ABI
    // .so files; a universal APK would pack every ABI at once.
    //   arm64-v8a = virtually all modern Android phones (64-bit ARM)
    //   x86_64    = emulators / some ChromeOS and Intel devices
    // armeabi-v7a (legacy 32-bit ARM) is intentionally excluded.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
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

    // Media3 ExoPlayer — the video player in the viewer (autoplay + custom controls).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Liquid glass (Kyant backdrop) for the floating nav bar. The lens effect needs API 33+.
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ViewModel + Compose lifecycle
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

    // Room (metadata cache)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Koin (DI) — the BOM keeps module versions in sync
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

    // Baseline Profile installer — applies the baseline profile (if present) at
    // install time so startup and scrolling are faster (ART AOT-compiles hot paths).
    implementation(libs.androidx.profileinstaller)

    // WorkManager — background auto-purge of the 30-day Trash.
    implementation(libs.androidx.work.runtime)

    // EXIF metadata reader (photos) — androidx.exifinterface
    implementation(libs.androidx.exifinterface)

    // HEIC/HEIF encoder (AndroidX) for the Format Converter. Note: android.media.HeifWriter
    // is a @hide class (not public API), so androidx.heifwriter is required.
    implementation(libs.androidx.heifwriter)

    // ZXing core — QR encoder (pure-Java, offline, no INTERNET permission) for
    // the QR Code Generator. The per-module matrix is rendered in a Compose Canvas.
    implementation(libs.zxing.core)

    // ML Kit Barcode Scanning (bundled) — on-device/offline QR decoder (no
    // INTERNET permission) for QR Detection. The model is embedded in the APK
    // (~2.4MB) and returns classified structures (URL/WiFi/contact/email/phone/geo).
    implementation(libs.mlkit.barcode.scanning)

    // ONNX Runtime (Android) — on-device AI inference engine for the AI model
    // framework (Background Remover in 2.0.0). Runs fully offline: models are
    // user-imported .onnx files, never fetched over the network (no INTERNET
    // permission). Uses XNNPACK acceleration with a CPU fallback; NNAPI is
    // avoided as it is deprecated and unstable across vendors.
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
