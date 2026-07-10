# Releasing & signing

Release APKs are built and published automatically by **GitHub Actions**
(`.github/workflows/release.yml`) whenever you push a `v*` tag. The workflow
decodes a keystore from repository secrets, builds `:app:assembleRelease`, and
attaches the signed APK to a GitHub Release. Signing is wired in
`app/build.gradle.kts` and reads credentials from **either** a local
`keystore.properties` file **or** environment variables (used in CI) — if
neither is present, a release build falls back to debug signing so local builds
never break.

## 1. Generate a keystore

```shell
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias agallery \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "YOUR_STORE_PASSWORD" \
  -keypass  "YOUR_KEY_PASSWORD" \
  -dname "CN=Your Name, O=AGallery, C=ID"
```

> ⚠️ **Never commit the keystore or its passwords.** `*.keystore` and
> `keystore.properties` are git-ignored. Keep a safe backup — losing the
> keystore means you can no longer ship updates under the same signature.

## 2. Add GitHub Actions secrets

In your repository go to **Settings → Secrets and variables → Actions → New
repository secret** and add these four secrets:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 release.keystore`). |
| `KEYSTORE_PASSWORD` | The store password. |
| `KEY_ALIAS` | The key alias (e.g. `agallery`). |
| `KEY_PASSWORD` | The key password. |

The workflow decodes `KEYSTORE_BASE64` back into `release.keystore` at build
time and passes the other three to Gradle as environment variables.

## 3. Sign locally (optional)

```shell
cp keystore.properties.example keystore.properties
```

```properties
storeFile=release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=agallery
keyPassword=YOUR_KEY_PASSWORD
```

```shell
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## 4. Cut a release

Bump the version (see below), commit, then tag and push:

```shell
git tag v2.3.0
git push origin v2.3.0
```

The workflow runs, builds the signed APK, and creates a **GitHub Release**
tagged `v2.3.0` with the APK attached and auto-generated release notes. You can
also trigger it manually from the **Actions** tab (`workflow_dispatch`).

---

## Versioning policy

AGallery follows **[Semantic Versioning](https://semver.org/)** (`MAJOR.MINOR.PATCH`).

- **Current version: `2.3.0` shipped; `2.4.0` next.** The `1.x`
  offline-utilities era is complete and the on-device AI era is well underway (see
  the roadmap in `TECHNICAL-DESIGN.md`); `2.0.0` shipped the AI model framework +
  Background Remover, `2.1.0` added **Subject Lift** (long-press to lift a photo's
  subject) on the same framework, and `2.1.1` was a maintenance patch (trimmed
  model catalog, cached/warmed ONNX session, a session-options leak fix, and
  top-bar padding fixes). **`2.2.0`** brought the **Image Upscaler** — on-device AI
  super-resolution with user-imported Real-ESRGAN models and Eco/Balanced/Quality
  tiers — plus **Auto Upscale** for batch processing. **`2.3.0` (current)** adds
  **Face Restore** — on-device AI restoration of blurry / low-quality faces, using
  user-imported GPEN models with on-device ML Kit face detection, a per-face
  restore strength, and a bounding-box preview of the faces that will be processed.
  These are the old "Image Enhancer" **split into separate AI capabilities**, each
  shipped and validated on its own. Still ahead: **Photo Restore / Enhance**
  (`2.4.0`, general denoise / sharpen / deblur), **Auto Enhance** (`2.5.0`, a
  one-tap pipeline that recombines face restore + restore + upscale), and then the
  non-AI **Image Compress** (`2.6.0`), **Smart Scanner** (`2.7.0`), **OCR → PDF**
  (`2.8.0`), and **AI Semantic Search** (`2.9.0`).
- **`PATCH`** (`1.0.1`, `1.0.2`, …) — bug fixes and pure visual polish
  (padding, colors, corner radius, animation tweaks) that add **no new
  capability**.
- **`MINOR`** (`1.1.0`, `1.2.0`, …) — new user-facing features or options
  (e.g. location/map view, a new tool, a new theme or setting).
- **`MAJOR`** (`2.0.0`) — a major product milestone or a breaking change
  (e.g. `2.0.0` introduces the on-device AI era).

Rule of thumb: *"just made the existing thing nicer"* → PATCH; *"added something
new you can use or choose"* → MINOR.

The version lives in `app/build.gradle.kts`:

```kotlin
versionCode = 24       // bump by 1 for every published build
versionName = "2.3.0"  // human-readable, matches the git tag (without the leading "v")
```
