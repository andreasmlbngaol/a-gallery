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
| `targetSdk` / `compileSdk` | 37 (compileSdk minor `37.1`) |
| `versionCode` / `versionName` | `20` / `2.1.0` |
| Language | Kotlin `2.4.0` (KSP `2.3.9`) |
| UI | Jetpack Compose (BOM `2026.06.01`), Material 3 `1.5.0-alpha23` |
| Build | AGP `9.2.1`, R8 (minify + shrink resources; **full mode disabled** so ML Kit consumer rules survive) |
| Packaging | Per-ABI APK splits (`arm64-v8a`, `x86_64`); no universal APK |

Signing/release is wired: `keystore.properties` locally or `KEYSTORE_*` env
vars in CI; a GitHub Actions workflow builds a signed release APK on a `v*` tag.
Release asset is named `AGallery-*.apk`.

### 2.2 Key libraries (all offline-friendly)

Coil 3 (images/video thumbs), Paging 3, Navigation 3, Telephoto (pinch-zoom),
Room (local cache), DataStore (settings), Koin (DI), Media3 (video),
WorkManager (background jobs), Haze + Kyant backdrop/shapes (glass UI),
Phosphor icons (Bold), AndroidX ExifInterface (metadata read/write),
AndroidX HeifWriter (HEIC/HEIF encode), ZXing core (offline QR encode),
ML Kit Barcode Scanning (bundled, on-device QR/barcode decode),
ONNX Runtime (Android) (on-device AI inference for user-imported `.onnx` models).

> Any new dependency must be offline-capable with a compatible license (prefer
> Apache-2.0 / MIT), added to `gradle/libs.versions.toml` **and**
> `THIRD-PARTY-NOTICES.md`. Do **not** use `coil-network-okhttp` (would add
> network use).

### 2.3 Architecture (Clean Architecture, 4 layers)

Package root: `id.andreasmbngaol.agallery`

```
core/            # common, di, image, navigation, permission, ui, ai (ONNX inference engine, device benchmark)
data/            # di, local (mediastore, prefs, room{dao,entity}), mapper, paging, repository, work, ai (model repo + background-removal processor)
domain/          # di, model (pure Kotlin, NO android.*), repository (interfaces), usecase — each with an ai/ subpackage
presentation/    # albums, gallery, home, settings, theme, trash, viewer, tools, ai, animation (+ per-feature di)
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
`Home` (a horizontal pager of 4 tabs: **Settings · Gallery · Albums · Tools**),
`PhotoViewer(...)`, `AlbumDetail(...)`, `Trash`, `CreateAlbum`, `QrGenerator`,
`AiModels`, `BackgroundRemover(...)`. The nav host is `AGalleryNavDisplay` with
shared-element transitions and predictive back.

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

**`1.x` — the localization & offline-utilities era (no `INTERNET` permission, no AI).** **Shipped through `1.7.1`; this era is complete.**

| Version | Scope | Status |
|---|---|---|
| `1.1.0` | **Localization foundation** — externalize every hardcoded UI string into Android string resources (no visible behavior change) | ✅ Shipped |
| `1.2.0` | **Bahasa Indonesia support** — full `id` translation (`values-id/`); English stays the default | ✅ Shipped |
| `1.3.0` | **Metadata Viewer** | ✅ Shipped |
| `1.4.0` | **Metadata Remover** | ✅ Shipped |
| `1.5.0` | **Format Converter** (JPG/PNG/WEBP/HEIC/HEIF) | ✅ Shipped |
| `1.6.0` | **Tools hub** + **QR Code Generator** | ✅ Shipped |
| `1.7.0` | **QR Detection** (classic computer vision via a library — not AI) | ✅ Shipped |

> Optional bulk / multi-select fast-follows may still ship as patch releases
> (**Metadata Remover batch `1.4.1`**, **Format Converter batch `1.5.1`**). A
> **Watermark** feature was previously planned for `1.8.0` but has been
> **dropped from the roadmap**.

**`2.0.0` — the on-device AI era.** Introduces the AI model framework
(Section 7) and the first AI feature. AI stays offline (models are
user-imported), so this is a *milestone* bump, not a permission/behavior break.
**`2.0.0` and `2.1.0` have shipped**; the rest of the `2.x` line is planned.

| Version | Scope | Status |
|---|---|---|
| `2.0.0` | AI model framework (ONNX Runtime) + **Background Remover** (first AI feature) | ✅ Shipped |
| `2.1.0` | **Subject Lift** — iOS-style long-press "lift" of a photo's subject in the viewer (drag, copy, share); reuses the Background Remover models & framework | ✅ Shipped |
| `2.2.0` | **Smart Scanner** module | ⏳ Planned |
| `2.3.0` | **OCR → PDF** | ⏳ Planned |
| `2.4.0` | **AI Semantic Search** — AGallery's only search (on-device) | ⏳ Planned |

> There is **no classic search**. Search arrives only as on-device semantic
> search in `2.4.0`. Document the milestone reasoning in `docs/releasing.md`.

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
| Background Remover | Yes | Viewer | 2.0.0 |
| Subject Lift | Yes | Viewer | 2.1.0 |
| Smart Scanner | Mixed (photo or capture) | Tools hub (+ viewer surfacing) | 2.2.0 |
| OCR → PDF | Mixed (capture / scan) | Tools hub | 2.3.0 |
| AI Semantic Search | No (searches library) | Search feature | 2.4.0 |

### 4.2 Navigation changes

- **Done (1.6.0):** the Home pager has a **4th tab** — `Settings · Gallery · Albums · Tools`.
- **Done:** each tool is its **own Nav3 route** pushed on the backstack, triggered
  from the hub — mirroring the existing `onOpenAlbum` / `onOpenTrash` pattern.
- A `Screen.Search` route is added only with AI Semantic Search (`2.4.0`).

---

## 5. Tools hub design

- The **Tools tab** shows a short header + a **2-column grid of tool cards**
  (Phosphor Bold icon, title, one-line description, optional status badge).
- **Group with section headers from day one** (e.g. **Utilities**, and **AI**
  now that 2.x has landed). AI cards can show a `Model needed` / `Ready` badge
  from the model registry (Section 7).
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

> **Watermark** — previously planned as the final `1.x` non-AI feature
> (`1.8.0`); **dropped from the roadmap** and no longer planned.

### AI utilities (2.x)

All depend on the AI model framework (Section 7).

- **Background Remover** (2.0.0, ✅ shipped) — *Viewer.* Removes a photo's
  background (salient-object cutout) and exports a transparent PNG, launched from
  the viewer's action sheet. Implemented in 2.0.0:
  - **Runtime** — ONNX Runtime (Android), CPU execution provider, fully offline.
  - **Model catalog** — three user-imported models grouped by quality tier:
    **U²-Net (Lite)** (320², ~5 MB, LIGHT), **IS-Net (General Use)** (1024²,
    BALANCED — the recommended default), and **BiRefNet (Lite)** (1024²,
    HIGH_QUALITY, heaviest). Each declares an input spec, a tier badge, and an
    estimated peak-memory figure.
  - **Device suitability guard** — before running, a lightweight `DeviceBenchmark`
    (total / available RAM + a CPU score) is weighed against the model's peak
    memory demand (`ModelSuitabilityEvaluator`, safe budget ≈ 55% of RAM). Models
    are rated **GOOD / SLOW / INSUFFICIENT_MEMORY**; an insufficient-memory model
    is blocked with a friendly message instead of letting the OS kill the process
    mid-inference.
  - **Live progress** — the processing dialog shows elapsed seconds and current
    process RAM (PSS) so a long run stays legible rather than a frozen spinner.
  - **Import UX** — the models screen opens each model's download page in the
    browser (view intent, no `INTERNET`), then imports and verifies the picked
    `.onnx` file into app-private storage; models can be deleted to reclaim space.
- **Subject Lift** (2.1.0, ✅ shipped) — *Viewer.* iOS-style **long-press to
  lift the subject**: press and hold a photo, its salient subject is cut out
  on-device and becomes a draggable sticker the user can move, then **Copy** or
  **Share** as a transparent PNG. Built entirely on the 2.0.0 Background Remover
  framework — same ONNX runtime and the same user-imported model catalog. In
  **AI Models**, an **Object Lifting** section picks which installed model powers
  the gesture (or **Auto** = smallest / fastest) and, for models that support it,
  the **Eco / Balanced / High** quality. Each model shows this device's
  suitability verdict (from `DeviceBenchmark`) as advice, so heavier models that
  would be slow or memory-tight here are flagged before selection.
- **Smart Scanner** (2.2.0) — *Tools hub (+ viewer surfacing).* An extensible
  on-device detector module; starts by surfacing QR detection, more detectors
  later.
- **OCR → PDF** (2.3.0) — *Tools hub.* Photograph notes / a whiteboard, OCR the
  text, and produce a PDF. Output format TBD (Section 9).
- **AI Semantic Search** (2.4.0) — *Search feature.* Search the library by
  meaning using on-device embeddings; all data stays local. This is AGallery's
  only search.

---

## 7. AI model framework (principles)

Shipped in `2.0.0`. The principles below held; the runtime is now **ONNX Runtime
(Android)** and the initial catalog is the three background-removal models
(Section 6). These principles remain the contract for future AI features:

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

### 8.1 Code style & documentation conventions

Established during the codebase-wide cleanup; all layers now follow these:

- **Documentation is English KDoc only.** Every file that needs explanation
  carries professional English KDoc. Do **not** write inline `//` narration —
  only KDoc, plus the rare short comment where truly unavoidable. UI strings
  and resource text are never rewritten by this rule.
- **One top-level declaration per file (Java style).** Each class / interface /
  object / enum lives in its own file named after it; no piggy-backed types.
- **Constructor-DSL Koin bindings.** Prefer `factoryOf(::Type)` /
  `singleOf(::Type)` over the lambda form (`factory { Type(get()) }`) wherever
  the constructor maps cleanly.
- **Sectioned packages.** Keep each feature's files grouped by package section
  (screen, view model, UI state, DI) so a feature stays self-contained.
- **Build config is documented too.** Gradle / version-catalog / properties
  files carry concise English comments for non-obvious build decisions only —
  no "check latest" style personal reminders.

---

## 9. Open decisions (remaining)

1. **AI runtime & specific models** — ✅ **decided for background removal.**
   Runtime is **ONNX Runtime (Android)** (no Google Play Services dependency,
   runs `.onnx` fully on-device); the initial models are U²-Net Lite, IS-Net
   General Use, and BiRefNet Lite. Runtime / model choices for later AI features
   (scanner, OCR, semantic search) are still open.
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
| Search screen | `presentation/search/` (2.4.0) |
| Nav routes | `core/navigation/NavKeys.kt` (`Screen.*`) + wire in `AGalleryNavDisplay` |
| New Home tab (Tools) | `presentation/home` (extend the pager to 4 pages) |
| DI wiring | one Koin module per feature |

---

*End of document. Update it as the open decisions in Section 9 are resolved and
as features ship.*
