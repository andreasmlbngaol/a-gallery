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
git tag v1.0.0
git push origin v1.0.0
```

The workflow runs, builds the signed APK, and creates a **GitHub Release**
tagged `v1.0.0` with the APK attached and auto-generated release notes. You can
also trigger it manually from the **Actions** tab (`workflow_dispatch`).

---

## Versioning policy

AGallery follows **[Semantic Versioning](https://semver.org/)** (`MAJOR.MINOR.PATCH`).

- **Current version: `1.0.0`** — the first feature-complete, stable release.
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
versionCode = 10       // bump by 1 for every published build
versionName = "1.0.0"  // human-readable, matches the git tag (without the leading "v")
```
