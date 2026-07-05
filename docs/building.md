# Building from source

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (latest canary/preview — the project uses **AGP 9.2** and **compileSdk 37**).
- **JDK 17+** (bundled with recent Android Studio).
- An Android device or emulator running **Android 10 (API 29) or newer**.

## Clone & open

```shell
git clone https://github.com/andreasmlbngaol/a-gallery.git
cd a-gallery
```

Open the folder in Android Studio and let Gradle sync. `local.properties`
(pointing at your SDK) is generated automatically and is **not** committed.

## Build a debug APK

```shell
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Or just press **Run** in Android Studio to install on a connected device.

## Dependency management

All versions are centralized in the Gradle **version catalog** at
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml). A few notes:

- **Material 3** is pinned to `1.5.0-alpha` explicitly so its Expressive APIs
  (`ButtonGroup`, `ToggleButton`) win over the version resolved by the Compose
  BOM.
- **KSP** must match the Kotlin version (`2.4.0`).
- **Coil 3** is used (`io.coil-kt.coil3`), not Coil 2.
- **Telephoto** uses the Coil 3 artifact (`zoomable-image-coil3`).

## Icons

The app uses **Phosphor Icons** only (`com.adamglin:phosphor-icon`). Material
Icons are intentionally not used. Every icon uses the **Bold** weight:

```kotlin
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Trash

Icon(PhosphorIcons.Bold.Trash, contentDescription = null)
```

A handful of intentionally solid glyphs use the `Fill` weight instead (e.g. the
filled heart / play / trash indicators).
