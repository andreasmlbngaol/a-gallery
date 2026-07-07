# AGallery — Technical Design & Roadmap

> **Purpose.** A single, self-contained blueprint for the next phase of
> AGallery, written so it can be pasted into any AI coding assistant as
> authoritative context. It states what is **already decided and certain**:
> the current architecture, the guiding principles, the versioning policy, and
> where each planned feature lives.
>
> **Feature specs here are intentionally high-level.** Detailed implementation
> for each feature is deferred and will be worked out one at a time, when that
> feature is built. This document defines *what* and *where*, not *how*.
>
> **Golden rules for any AI assistant using this doc**
> 1. AGallery is **fully offline**. The app must **never** declare the
>    `INTERNET` permission and must never upload user data.
> 2. All processing (including AI) runs **on-device**.
> 3. Match the existing architecture, package layout, and conventions in
>    Section 2 before inventing new ones.
> 4. Keep the navigation bar minimal. Features are reached through the Photo
>    Viewer (contextual) or the single Tools tab — not through new nav tabs.

---

## 1. Product identity & principles

- **Offline-only.** No `INTERNET` permission in the manifest — a *verifiable*
  privacy guarantee, not just a promise. Works fully with no network, forever.
- **No account, no ads, no tracking.** No sign-in, no analytics, no network
  calls of any kind.
- **On-device by construction.** Even AI runs locally. The app never fetches
  models itself; the user brings them in manually (Section 7).
- **User owns their storage.** The app manages media directly (move / rename /
  delete) without a system prompt on every action (requires all-files access,
  Section 8).
- **Smooth & modern UI** with a consistent visual system (`ComponentStyle`,
  Phosphor icons).

---

## 2. Current state (ground truth)

### 2.1 Build configuration

| Key | Value |
|---|---|
| `namespace` / `applicationId` | `id.andreasmbngaol.agallery` |
| `minSdk` | **29 (Android 10)** |
| `targetSdk` / `compileSdk` | 37 |
| `versionCode` / `versionName` | `10` / `1.0.0` |
| Language | Kotlin `2.4.0` (KSP `2.3.9`) |
| UI | Jetpack Compose (BOM `2026.06.01`), Material 3 `1.5.0-alpha23` |
| Build | AGP `9.2.1`, R8 full mode (minify + shrink resources) |

Signing/release is wired: `keystore.properties` locally or `KEYSTORE_*` env
vars in CI; a GitHub Actions workflow builds a signed release APK on a `v*` tag.
Release asset is named `AGallery-*.apk`.

### 2.2 Key libraries (all offline-friendly)

Coil 3 (images/video thumbs), Paging 3, Navigation 3, Telephoto (pinch-zoom),
Room (local cache), DataStore (settings), Koin (DI), Media3 (video),
WorkManager (background jobs), Haze + Kyant backdrop/shapes (glass UI),
Phosphor icons (Bold).

> Any new dependency must be offline-capable with a compatible license (prefer
> Apache-2.0 / MIT), added to `gradle/libs.versions.toml` **and**
> `THIRD-PARTY-NOTICES.md`. Do **not** use `coil-network-okhttp` (would add
> network use).

### 2.3 Architecture (Clean Architecture, 4 layers)

Package root: `id.andreasmbngaol.agallery`

```
core/            # common, di, image, navigation, permission, ui
data/            # di, local (mediastore, prefs, room{dao,entity}), mapper, paging, repository, work
domain/          # di, model (pure Kotlin, NO android.*), repository (interfaces), usecase
presentation/    # albums, gallery, home, settings, theme, trash, viewer, animation (+ per-feature di)
```

Conventions that MUST be followed:

- `domain/model` is **pure Kotlin** — never import `android.*` there.
- Repository **interfaces** in `domain/repository`, implementations in
  `data/repository`.
- **Use cases** in `domain/usecase`, one responsibility each.
- **DI**: one Koin module per feature.
- **Icons**: `PhosphorIcons.Bold.<Icon>` (Fill weight only where already used).
- **Styling**: every floating/system surface honors `ComponentStyle`
  (SOLID / FROSTED / GLASS) and `EdgeEffectMode`.

### 2.4 Navigation (Navigation 3)

Routes are a `sealed interface Screen : NavKey` (all `@Serializable`). Current:
`Home` (a horizontal pager of 3 tabs: **Settings · Gallery · Albums**),
`PhotoViewer(...)`, `AlbumDetail(...)`, `Trash`, `CreateAlbum`. The nav host is
`AGalleryNavDisplay` with shared-element transitions and predictive back.

### 2.5 Core domain models (already implemented)

- `MediaItem { id, uri, displayName, mimeType, type, dateAddedEpochSeconds, bucketId, bucketName, durationMs, isFavorite }`
- `MediaDetails { sizeBytes, width, height, relativePath }` — loaded on-demand
  in the viewer's swipe-up detail panel. **Extension point for the Metadata
  Viewer.**
- `MediaScope` (sealed): `Camera, AllMedia, AllVideos, Screenshots, ScreenRecordings, Favorites, Trash, Bucket(bucketId)`.
- `Album { key, scope, name, coverUri, photoCount, videoCount, isSmart }`.
- `AppSettings { edgeEffectMode?, componentStyle?, gridColumns(3..5), sortOrder, performanceMode, pinnedAlbumKeys? }`.
- `GallerySortOrder { DateDesc, DateAsc }`, `ComponentStyle { SOLID, FROSTED, GLASS }`, `EdgeEffectMode { OFF, DARKEN, BLURRY }`.

> **Automatic (smart) albums are only `Recent`, `Videos`, and `Favorites`.**
> Everything else in the Albums tab is the device's own folders, optionally
> pinned.

---

## 3. Versioning policy

AGallery uses **product versioning** (SemVer-shaped, but MAJOR marks a product
milestone, not strictly an API break — this is an app, not a library).

| Bump | Meaning |
|---|---|
| **MAJOR** (`x.0.0`) | A major product milestone **or** a genuine breaking change |
| **MINOR** (`1.x.0`) | A new user-facing feature (additive) |
| **PATCH** (`1.0.x`) | Bug fixes and pure visual polish |

### 3.1 Release map

**`1.x` — the localization & offline-utilities era (no `INTERNET` permission, no AI).**

| Version | Scope |
|---|---|
| `1.1.0` | **Localization foundation** — externalize every hardcoded UI string into Android string resources (no visible behavior change) |
| `1.2.0` | **Bahasa Indonesia support** — full `id` translation (`values-id/`); English stays the default |
| `1.3.0` | **Metadata Viewer** |
| `1.4.0` | **Metadata Remover** |
| `1.5.0` | **Format Converter** (JPG/PNG/WEBP/HEIC/HEIF) |
| `1.6.0` | **Tools hub** + **QR Code Generator** |
| `1.7.0` | **QR Detection** (classic computer vision via a library — not AI) |
| `1.8.0` | **Watermark** (the final non-AI feature) |

**`2.0.0` — the on-device AI era.** Introduces the AI model framework
(Section 7) and the first AI feature. AI stays offline (models are
user-imported), so this is a *milestone* bump, not a permission/behavior break.

| Version | Scope |
|---|---|
| `2.0.0` | AI model framework + **Background Remover** (first AI feature) |
| `2.1.0` | **Smart Scanner** module |
| `2.2.0` | **OCR → PDF** |
| `2.3.0` | **AI Semantic Search** — AGallery's only search (on-device) |

> There is **no classic search**. Search arrives only as on-device semantic
> search in `2.3.0`. Document the milestone reasoning in `docs/releasing.md`.

---

## 4. Feature-placement architecture

Every feature lands in one of **three homes**, decided by one question: *does it
operate on a photo that already exists?*

1. **Photo Viewer (contextual)** — acts on one existing photo (or a
   multi-selection). Reached from an actions menu / bottom sheet in the viewer
   and the album multi-select bar.
2. **Tools hub (standalone)** — a tool that does **not** start from an existing
   library photo (it creates or captures). One nav tab, a grid of tool cards,
   each card opens its own full page.
3. **Search** — semantic search is part of the Search feature, not a tool.

### 4.1 Placement table

| Feature | Starts from existing photo? | Home | Version |
|---|---|---|---|
| Localization foundation | No (app-wide) | Codebase-wide (string resources) | 1.1.0 |
| Bahasa Indonesia support | No (app-wide) | `res/values-id/` | 1.2.0 |
| Metadata Viewer | Yes | Viewer (detail panel) | 1.3.0 |
| Metadata Remover | Yes (single / batch) | Viewer + multi-select | 1.4.0 |
| Format Converter | Yes (single; batch in 1.5.1) | Viewer (detail panel) | 1.5.0 |
| QR Code Generator | No (creates) | Tools hub | 1.6.0 |
| QR Detection | Yes | Viewer | 1.7.0 |
| Watermark | Yes (single / batch) | Viewer + multi-select | 1.8.0 |
| Background Remover | Yes | Viewer | 2.0.0 |
| Smart Scanner | Mixed (photo or capture) | Tools hub (+ viewer surfacing) | 2.1.0 |
| OCR → PDF | Mixed (capture / scan) | Tools hub | 2.2.0 |
| AI Semantic Search | No (searches library) | Search feature | 2.3.0 |

### 4.2 Navigation changes

- Add a **4th tab** to the Home pager: `Settings · Gallery · Albums · Tools`.
- Each tool becomes its **own Nav3 route** pushed on the backstack, triggered
  from the hub — mirroring the existing `onOpenAlbum` / `onOpenTrash` pattern.
- A `Screen.Search` route is added only with AI Semantic Search (`2.3.0`).

---

## 5. Tools hub design

- The **Tools tab** shows a short header + a **2-column grid of tool cards**
  (Phosphor Bold icon, title, one-line description, optional status badge).
- **Group with section headers from day one** (e.g. **Utilities**, and **AI**
  once 2.x lands). AI cards can show a `Model needed` / `Ready` badge from the
  model registry (Section 7).
- Each tool is a normal full screen with its own ViewModel + Koin module under
  `presentation/tools/<tool>/`.
- A **single declarative list of tools** is the source of truth for the hub, so
  adding a tool is one entry plus its screen.

---

## 6. Feature specs (high-level)

Each spec is deliberately brief — Goal, Placement, Version — plus only what is
already certain. Detailed design is done per-feature at build time.

### Localization (1.x)

- **Localization foundation** (1.1.0) — *Codebase-wide.* Externalize every
  hardcoded, user-facing string into `res/values/strings.xml` and replace call
  sites with `stringResource(...)` (Compose) or a resource-backed string
  provider (ViewModels / workers). No behavior or copy change — this is the
  groundwork that makes translation possible. English (`values/`) is the
  default locale. Recommended: a lint/CI guard against new hardcoded UI strings.
- **Bahasa Indonesia support** (1.2.0) — *`res/values-id/`.* Add a complete
  Indonesian translation of every string key introduced in 1.1.0. The app
  follows the system locale; English remains the fallback. An in-app language
  switcher is out of scope for now (may come later).

### Non-AI utilities (1.x)

- **Metadata Viewer** (1.3.0) — *Viewer.* Show richer metadata (e.g. date
  taken, camera, ISO, dimensions, location, etc.) by extending the existing
  `MediaDetails` detail panel. **Location:** show the coordinates and offer
  "open in Google Maps" via an intent — **no in-app map** (keeps the app
  offline).
- **Metadata Remover** (1.4.0) — *Viewer (detail panel).* Strip metadata before
  sharing, launched from the swipe-up detail panel (photos only). Implemented in
  1.4.0:
  - **Selective removal** — the user picks what to strip: **Location (GPS)**,
    **Camera & settings**, or **All metadata**. Orientation is always preserved
    so the photo never ends up rotated.
  - **Output choice, asked every time** — **overwrite the original** or **save a
    clean copy** (`*_clean`) in the same folder; the copy path never touches the
    original.
  - **Lossless** — uses `ExifInterface.saveAttributes()` (no re-encode) on
    **JPEG / PNG / WebP**. **HEIC/HEIF is skipped** for now (read-only for
    writing in the framework; lossless strip would need a native lib like
    exiv2/libheif — out of scope). Unsupported formats show a friendly message.
  - **Consent** reuses the existing write-request / `RecoverableSecurityException`
    flow from rename/move (auto-retries after the user approves).
  - **Bulk / multi-select** removal is a planned fast-follow (1.4.1).
- **Format Converter** (1.5.0) — *Viewer (detail panel).* Convert a single photo
  between **JPG, PNG, WEBP, HEIC** (HEIF shares the HEIC container, so it's
  offered as HEIC), launched from the swipe-up detail panel (photos only).
  Primary use case: rescue HEIC/HEIF into JPG/PNG, or shrink to WEBP. Implemented
  in 1.5.0:
  - **Target picker** — the source's current format is disabled (no converting a
    file to itself). All other formats are selectable.
  - **Quality slider** (1–100, default 95) shown only for **lossy** targets
    (JPG / WEBP / HEIC). **PNG** is lossless, so the slider is hidden.
  - **Decode** via `ImageDecoder` into a software bitmap; orientation is baked
    into the pixels, then the output EXIF orientation is reset to `NORMAL` to
    avoid a double-rotate.
  - **Encode** — JPG/PNG/WEBP via `Bitmap.compress` (WEBP uses `WEBP_LOSSY` on
    API 30+, legacy `WEBP` on API 29). **HEIC** via `android.media.HeifWriter`
    (bitmap input mode) into a cache temp file, then copied into the MediaStore
    entry. HeifWriter depends on the device's HW encoder; if it's unavailable the
    conversion fails cleanly with an "unsupported on this device" message.
  - **Transparency → no-alpha targets** — JPG and HEIC have no alpha, so
    transparent areas are **flattened onto black** before encoding.
  - **Metadata** — for JPG/PNG/WEBP the full EXIF set (date, camera, GPS, misc)
    is copied best-effort to the result, orientation reset to normal. HEIC skips
    EXIF carry-over (HeifWriter / androidx `ExifInterface` can't write HEIF).
  - **Output choice, asked every time** — **keep the original + new file**, or
    **replace the original** (which moves it to the Room-based soft Trash, no
    MediaStore consent needed). Conversion always writes a **new file** in the
    **same folder**, with a unique name (`_1`, `_2`, … on collision).
  - **Bulk / multi-select** conversion is a planned fast-follow (1.5.1).
- **QR Code Generator** (1.6.0) — *Tools hub.* Create a customizable, modern QR
  code, optionally with a title/subtitle and a center logo or photo.
- **QR Detection** (1.7.0) — *Viewer.* Detect and read a QR/barcode in a photo.
  **Not AI** — done with a classic computer-vision library, fully offline, no
  model download.
- **Watermark** (1.8.0, final non-AI feature) — *Viewer + multi-select.* Overlay
  a text or image watermark on a photo. **No default preset.**

### AI utilities (2.x)

All depend on the AI model framework (Section 7).

- **Background Remover** (2.0.0) — *Viewer.* Remove or replace a photo's
  background (subject cutout); export a transparent PNG.
- **Smart Scanner** (2.1.0) — *Tools hub (+ viewer surfacing).* An extensible
  on-device detector module; starts by surfacing QR detection, more detectors
  later.
- **OCR → PDF** (2.2.0) — *Tools hub.* Photograph notes / a whiteboard, OCR the
  text, and produce a PDF. Output format TBD (Section 9).
- **AI Semantic Search** (2.3.0) — *Search feature.* Search the library by
  meaning using on-device embeddings; all data stays local. This is AGallery's
  only search.

---

## 7. AI model framework (principles)

Build this first in `2.0.0`. Only the principles are fixed; the runtime and
specific models are still open (Section 9).

- **No `INTERNET` permission.** The app never downloads models itself.
- **The user brings the model.** The app opens the model's download page in the
  user's **browser** (a view intent — which does **not** require `INTERNET` for
  AGallery), the user selects the downloaded file, and the app imports it.
- **Verification is automatic and invisible.** The app validates the imported
  file; the user never sees technical terms.
- **Decouple acquisition from use.** Inference code only needs a **file path**;
  how the file arrived is a separate concern. A feature can be built against a
  manually-placed model before the import UX is finished.
- **Private storage + removable.** Models live in app-private storage and can be
  deleted to reclaim space. A single model registry drives the Tools hub badges
  and per-feature setup screens.

---

## 8. Cross-cutting conventions

- **Permissions.** Existing media-read permissions **plus all-files access**
  (`MANAGE_EXTERNAL_STORAGE`) so move/rename/delete work without a system
  prompt on every action — acceptable because AGallery is distributed via
  GitHub Releases, not Google Play. **Never add `INTERNET`** (a CI/lint guard
  that fails the build if `INTERNET` appears in the merged manifest is
  recommended).
- **Outputs.** Derived files go to a dedicated app folder and are registered
  with MediaStore; never silently overwrite originals.
- **Background work.** Heavy or batch operations run off the main thread
  (WorkManager for long jobs, with progress).
- **Layering.** New logic goes into domain use cases + repository interfaces,
  implemented in `data`.
- **UI consistency.** Honor `ComponentStyle` & `EdgeEffectMode`; Phosphor Bold
  icons; respect grid density, sort, and the existing transition language.
- **Accessibility & i18n.** All user-facing strings localizable (en + id);
  content descriptions on icon buttons.
- **Licensing.** App license: **PolyForm Noncommercial 1.0.0** (© 2026
  andreasmlbngaol). Every new dependency is added to `THIRD-PARTY-NOTICES.md`;
  each imported AI **model** has its own license — record and surface it.

---

## 9. Open decisions (remaining)

1. **AI runtime & specific models** — undecided. Criteria: best on-device
   quality/performance, no Google Play Services dependency, and a wide choice of
   good, freely-downloadable models.
2. **OCR → PDF output** — undecided (searchable image+text PDF vs. reflowed
   text). Decide when building the feature.

---

## 10. Quick reference — module placement for new code

| New code | Package |
|---|---|
| Pure models | `domain/model` (no `android.*`) |
| Repository interfaces | `domain/repository` |
| Use cases | `domain/usecase` |
| MediaStore / EXIF / model IO implementations | `data/...` + `data/repository` |
| Tool screens + ViewModels | `presentation/tools/<tool>/` (+ `di`) |
| Search screen | `presentation/search/` (2.3.0) |
| Nav routes | `core/navigation/NavKeys.kt` (`Screen.*`) + wire in `AGalleryNavDisplay` |
| New Home tab (Tools) | `presentation/home` (extend the pager to 4 pages) |
| DI wiring | one Koin module per feature |

---

*End of document. Update it as the open decisions in Section 9 are resolved and
as features ship.*
