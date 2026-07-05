# AGallery — Technical Documentation

Welcome to the developer documentation for **AGallery**, a modern, offline-first
photo & video gallery for Android built entirely with Jetpack Compose.

> Looking for the user-facing overview or a download link? See the
> [project README](../README.md).

## Contents

- **[Architecture](./architecture.md)** — layers, data flow, and project structure.
- **[Building from source](./building.md)** — prerequisites, cloning, and debug builds.
- **[Releasing & signing](./releasing.md)** — keystores, CI, tags, and the versioning policy.
- **[Third-party licenses](./third-party-licenses.md)** — every bundled dependency and its license.

---

## Overview

Most stock galleries are heavy, ad-laden, or tied to a cloud account. AGallery
is the opposite: a lightweight, **offline-first** viewer that treats the device
itself as the single source of truth. It queries `MediaStore` through a paged
data source so even libraries with tens of thousands of items scroll without
jank, caches lightweight metadata in Room, and persists user preferences in a
typed DataStore.

The UI is deliberately expressive: a floating **liquid-glass** navigation bar
(real-time refraction via the Kyant `backdrop` library on Android 13+, with a
frosted fallback below), a shared-element full-screen viewer, and a long-press
"peek" that grows a thumbnail to its original aspect ratio over a blurred
backdrop.

## Tech stack

### App & UI

- **Kotlin 2.4** targeting **Android 8.0+ (minSdk 29)**, compiled against **SDK 37** with **AGP 9.2**.
- **Jetpack Compose** (BOM `2026.06.01`) — 100% Compose UI, no XML layouts.
- **Material 3 `1.5.0-alpha` (Expressive)** — pinned explicitly for `ButtonGroup` / `ToggleButton` APIs not yet in the BOM-managed stable.
- **Navigation 3** (`navigation3-runtime` / `-ui`) with ViewModel scoping for Nav3.
- **AndroidX Splash Screen** + edge-to-edge.

### Architecture & DI

- **Clean architecture** — `presentation` → `domain` → `data`, feature-first modules.
- **Koin 4.2** (BOM) — dependency injection across repositories, use cases, and ViewModels.
- **Kotlinx Coroutines / Flow** — reactive state throughout.

### Media & data

- **`MediaStore`** — the single source of truth for photos & videos.
- **Jetpack Paging 3** — paged media loading.
- **Room 2.8** — local cache for lightweight metadata (albums, favorites).
- **DataStore 1.2** (typed / `kotlinx.serialization`) — persisted user settings.
- **WorkManager** — background auto-purge of Trash after 30 days.
- **Accompanist Permissions** — runtime media-permission handling (partial-access aware on Android 14+).

### Media playback, design & icons

- **Coil 3.5** (`coil-compose` + `coil-video`) — image & video-frame loading and caching.
- **Media3 ExoPlayer** — in-app video playback with custom controls.
- **Telephoto 0.19** — pinch-to-zoom in the full-screen viewer.
- **Kyant `backdrop` 2.0 + `shapes` 1.2** — the liquid-glass refraction effect.
- **Haze** — background blur for frosted surfaces.
- **Phosphor Icons** — the app's icon set (Material Icons are intentionally **not** used); all icons use the **Bold** weight.

## Feature status (v1.0.0)

| Area | Status |
|---|---|
| Browse photos & videos (paged grid) | ✅ |
| Smart albums + folder albums | ✅ |
| Create new album (copy-based) | ✅ |
| Favorites | ✅ |
| Full-screen viewer + pinch-to-zoom | ✅ |
| In-app video playback | ✅ |
| Multi-select batch copy / move / delete | ✅ |
| Copy / Move to album (thumbnail picker) | ✅ |
| Trash / recycle bin (restore + auto-purge) | ✅ |
| Share & set as wallpaper | ✅ |
| Sort (date asc/desc) | ✅ |
| Settings (grid density, component style) | ✅ |
| Search | ⏳ planned |
| Location / map view | ⏳ planned |
| Photo editing | ❌ out of scope |
