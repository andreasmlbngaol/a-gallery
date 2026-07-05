# Third-party licenses

AGallery bundles open-source libraries. The complete attribution list — every
dependency, its copyright holder, and its license, together with the full text
of the Apache License 2.0 and the MIT License — is maintained in a single file
at the repository root:

👉 **[THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md)**

## Summary

| License | Libraries |
|---|---|
| **Apache License 2.0** | All AndroidX / Jetpack (Compose, Material 3, Room, Paging, DataStore, Navigation 3, Media3, WorkManager, Lifecycle, SplashScreen, ProfileInstaller, Core KTX), Kotlin & kotlinx (Coroutines, Serialization), Coil, Koin, Accompanist, Telephoto, Haze, Kyant Backdrop & Shapes |
| **MIT** | Phosphor Icons (Compose port + icon set) |

Test-only and build-only tools (JUnit, Espresso, AndroidX Test, AGP, KSP) are
**not** shipped inside the released APK and therefore carry no distribution
obligation; they are listed in the notices file for completeness.

## What this means for distribution

Both Apache-2.0 and MIT require that redistributions **retain the copyright and
license notices**. AGallery satisfies this by shipping
[`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md) with the source and
releases.

> 💡 Optional: to also surface these notices *inside the app* (an "Open source
> licenses" screen), you can add a generator such as
> [AboutLibraries](https://github.com/mikepenz/AboutLibraries) or the
> [Google OSS Licenses plugin](https://developers.google.com/android/guides/opensource).
