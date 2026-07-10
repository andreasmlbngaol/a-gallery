# Plan — `2.4.1`: Selective XNNPACK acceleration for the Upscaler

> **Status:** ⏳ Planned (patch). To be implemented **after `2.4.0` ships**.
> **Type:** `PATCH` (performance; no new user-facing capability).
> **Owner note:** This document is self-contained. A fresh contributor (or a
> new AI chat) should be able to execute it end-to-end with only this file and
> the project source.

---

## 1. Motivation

All AI features currently run on ONNX Runtime's **plain CPU execution provider**
(see `core/ai/OnnxInferenceEngine.kt`). On a mid-range SoC (e.g. Helio G99, 2
performance cores) a 1920×1080 photo through the **Upscaler** or **Photo
Enhance** is split into ~40-48 tiles and can take **~5 minutes**. That is
inherent to running heavy restoration networks on a mobile CPU, but we asked a
narrower question:

> Can the **XNNPACK execution provider** accelerate any of our models *without*
> hurting quality, and *without* the bilinear-`Resize` session-build failure
> that forced us to keep XNNPACK **off by default**?

XNNPACK is a highly optimized CPU kernel library (SIMD-heavy Conv/GEMM). It only
helps when the graph is **Conv/activation-dominated**; transformer-style graphs
(MatMul/Softmax/LayerNorm/Transpose) fall back to CPU op-by-op and gain little,
and some ops (notably **`Resize` with `mode=linear`**) make the XNNPACK session
build **abort** — a failure the app already guards against.

To answer this empirically we ran a static ONNX graph scan over all 8 catalog
models (`docs/xnnpack_graph_check.py`; requires `pip install onnx`). Results in
§2.

---

## 2. Model graph analysis (evidence)

Scanned with ONNX opset/IR per model; counts are node histograms.
`conv = Conv + ConvTranspose`. `Resize(lin)` = `Resize` nodes with
`mode=linear`/`cubic`, which is what breaks the XNNPACK session build.

| Model | Feature | opset | Nodes | conv | Resize | Resize(lin) | CPU-fallback ops | Verdict |
|---|---|---|---|---|---|---|---|---|
| **real_esrgan_x4plus-single** | **Upscale** | 21 | 1094 | **351** | 2 (nearest) | 0 | 0 | ✅ **Good candidate** |
| **real_esrgan_general_x4v3-single** | **Upscale** | 21 | 70 | **34** | 1 (nearest) | 0 | 1 (`DepthToSpace`) | ✅ **Good** (see PRelu caveat) |
| SCUNet-GAN | Enhance | 14 | 6639 | 120 | 0 | 0 | 4652 / 18 types | ❌ Not worth it |
| SCUNet-PSNR | Enhance | 14 | 6639 | 120 | 0 | 0 | 4652 / 18 types | ❌ Not worth it |
| GPEN-BFR-512 | Face restore | 10 | 1332 | 52 | 0 | 0 | 1036 / 15 types | ❌ Not worth it |
| gpen_bfr_256 | Face restore | 15 | 1208 | 45 | 0 | 0 | 946 / 15 types | ❌ Not worth it |
| isnet-general-use-dynamic | Remove bg | 13 | 643 | 119 | 39 | **39** | + Constant/Shape/Slice | ⛔ **Blocked** (linear Resize) |
| u2netp | Remove bg | 11 | 1055 | 119 | 38 | **38** | + 6 types | ⛔ **Blocked** (linear Resize) |

### Interpretation

- **Upscaler (Real-ESRGAN) — the only real win.** Both models are almost
  entirely `Conv` + activation (`PRelu` on x4v3; `LeakyRelu`/`Concat`/`Add`/
  `Mul`/`Clip` on x4plus). Their `Resize` nodes are **`mode=nearest`**
  (`coordinate_transformation_mode=asymmetric`) — the variant XNNPACK is much
  more likely to accept, **not** the blocking `linear` kind. `x4plus` scored
  "all ops in supported set"; `x4v3` has a single `DepthToSpace` (pixel-shuffle
  upsample) that partitions to CPU — harmless (one node).
- **Photo Enhance (SCUNet) — do NOT enable.** Only **117 of 6639 nodes are
  Conv (~2%)**. SCUNet is a **Swin-Conv (transformer)** network: the bulk is
  `Gather`/`Unsqueeze`/`MatMul`/`Softmax`/`Einsum`/`Erf`/`ScatterND`/`Transpose`
  — all CPU fallback. XNNPACK would build a session but run almost everything on
  CPU anyway, just adding build overhead. **The slow enhance is inherent and
  cannot be meaningfully accelerated via XNNPACK.**
- **Face Restore (GPEN) — do NOT enable.** StyleGAN-based; modulated convs
  decompose into hundreds of `Reshape`/`Transpose`/`Pad`/`Gemm`/`Slice`. Heavy
  fallback, negligible gain.
- **Background Remover (ISNet, U²-Netp) — blocked, keep on CPU.** 38-39
  `Resize` nodes with `mode=linear` (`half_pixel` / `pytorch_half_pixel`). This
  is exactly the `xnn_create_resize_bilinear2d_nhwc_fp32` build failure the
  existing crash-guard documents. They are also already fast enough.

**Conclusion:** enable XNNPACK **only** for the two Real-ESRGAN upscaler models;
keep every other feature on the CPU provider.

---

## 3. Design

Today XNNPACK is gated by a single **global** per-device flag
(`AccelerationConfig.isXnnpackEnabled()`, **off by default**) and
`OnnxInferenceEngine.buildOrtSession` reads it for *every* model. We need
**per-model** control so eligible upscaler models can attempt XNNPACK while
everything else stays on CPU.

### 3.1 Approach

1. **Mark eligibility in the catalog.** Add a boolean to the model spec
   (`domain/model/ai/AiModelSpec.kt`), e.g. `val xnnpackEligible: Boolean = false`.
   Set it `true` **only** for the two Real-ESRGAN upscaler entries in
   `domain/model/ai/ModelCatalog.kt` (`real_esrgan_general_x4v3-single` and
   `real_esrgan_x4plus-single`). Everything else stays `false`.
2. **Thread an `allowXnnpack` flag into the engine.** Extend the
   `InferenceEngine` interface (`core/ai/InferenceEngine.kt`):
   - `acquireSession(modelPath: String, allowXnnpack: Boolean)`
   - `createSession(modelPath: String, allowXnnpack: Boolean)` (import validation
     can always pass `false`).
   Update `OnnxInferenceEngine` accordingly. In `buildOrtSession`, attempt the
   XNNPACK path only when `allowXnnpack == true` **and** the crash-guard has not
   disabled it. Cache key stays `modelPath`; also fold the provider choice into
   the warm-cache identity so a CPU session is never reused as an XNNPACK one
   (safest: key the cache on `modelPath + provider`).
3. **Pass the flag from processors.** `data/ai/ImageUpscaleProcessor.kt` passes
   the upscaler spec's `xnnpackEligible`. `BackgroundRemovalProcessor`,
   `FaceRestoreProcessor`, and `PhotoEnhanceProcessor` pass `false`.
   `data/ai/AiModelRepositoryImpl.kt` (model-import validation via
   `createSession`) passes `false`.
4. **Default-on for eligible models, still crash-guarded.** For eligible
   upscaler models, attempt XNNPACK **by default** (this is the whole point of
   the patch), but keep the existing **write-ahead crash guard**
   (`beginXnnpackProbe` / `endXnnpackProbe` / `recoverFromCrashIfNeeded`) so a
   native SIGSEGV during session build permanently falls the device back to CPU.
   Keep the existing global user toggle as a hard off-switch/override.

### 3.2 Files to touch

- `core/ai/InferenceEngine.kt` — interface signatures (`allowXnnpack`).
- `core/ai/OnnxInferenceEngine.kt` — honor `allowXnnpack`; provider-aware cache
  key; keep crash guard + `buildOptions(threads, xnnpack)` as-is.
- `core/ai/AccelerationConfig.kt` — allow "eligible models attempt XNNPACK by
  default"; keep crash-guard semantics (may add a small helper).
- `domain/model/ai/AiModelSpec.kt` — add `xnnpackEligible: Boolean = false`.
- `domain/model/ai/ModelCatalog.kt` — set `xnnpackEligible = true` on the two
  Real-ESRGAN upscaler specs only.
- `data/ai/ImageUpscaleProcessor.kt` — pass the spec flag into `acquireSession`.
- `data/ai/{BackgroundRemovalProcessor,FaceRestoreProcessor,PhotoEnhanceProcessor}.kt`
  and `data/ai/AiModelRepositoryImpl.kt` — pass `allowXnnpack = false`.

No string/UI changes required (this is invisible to users except "upscale is
faster"). If desired, a single dev-only log line reporting the chosen provider
is fine.

---

## 4. On-device verification (MUST do before shipping)

The desktop `onnxruntime` wheel does **not** ship the XNNPACK EP, and the
Android XNNPACK build's supported-op set can differ, so these can only be
confirmed on a real device:

1. **Session builds without crashing** for both Real-ESRGAN models (the
   `nearest`+`asymmetric` `Resize` must be accepted; if it SIGSEGVs, the crash
   guard should catch it on next launch and fall back to CPU — verify that path
   too).
2. **Node placement.** Temporarily raise ORT log severity to INFO and read
   logcat for `Node(s) placed on [XnnpackExecutionProvider]. Number of nodes: N`.
   Confirm the **Conv** nodes actually land on XNNPACK.
   - ⚠️ **`PRelu` caveat (x4v3):** if the Android XNNPACK build does **not**
     support `PRelu` (33 nodes), x4v3's graph will fragment into many tiny
     partitions and lose the speedup. `x4plus` uses `LeakyRelu`/`Clip` and is
     the safer bet. **Decision rule:** keep `xnnpackEligible = true` only for
     models where the majority of Conv nodes are confirmed on XNNPACK; otherwise
     revert that model to CPU.
3. **Wall-clock A/B.** Same 1080p photo, XNNPACK vs CPU, same tile size and
   thread count; record tiles/sec and total time. Ship only if XNNPACK is
   clearly faster and output is visually identical.
4. **Thermals / stability.** Run 3-4 large images back-to-back; confirm no
   crash, no memory regression (the low-memory session options must stay).

---

## 5. Risks & rollback

- **Native crash on session build** → covered by the existing write-ahead crash
  guard (permanently disables XNNPACK for that device). Do not remove it.
- **No real speedup (heavy fragmentation)** → set that model's
  `xnnpackEligible = false`; zero code risk since CPU is the default path.
- **Quality drift** → XNNPACK FP32 should be numerically equivalent; verify with
  an A/B on a detailed photo. If any artifact appears, disable.
- **Full rollback** = flip both catalog flags to `false` (behaves exactly like
  today's CPU-only path).

---

## 6. Out of scope for `2.4.1`

- Accelerating **Enhance (SCUNet)** or **Face Restore (GPEN)** — graph analysis
  shows XNNPACK cannot help; would need INT8/FP16 quantized model variants
  (quality trade-off) or a different runtime — a separate, larger effort.
- **NNAPI / GPU EPs** — unreliable for arbitrary user-imported `.onnx` on the
  budget MediaTek SoCs we target; NNAPI is deprecated on Android 15.
- Any change to the Background Remover / Subject Lift path (stays CPU; blocked
  by linear `Resize`).

---

## 7. Release steps

1. Implement §3, verify §4 on a real device.
2. Bump the version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 26        // was 25
   versionName = "2.4.1"   // was "2.4.0"
   ```
3. Update the roadmap notes if the outcome differs from the plan (e.g. only
   `x4plus` ended up eligible).
4. Tag & push `v2.4.1` (CI builds and publishes the signed APK — see
   `docs/releasing.md`).

---

## Appendix A — reproduce the graph scan

```bash
pip install onnx
python3 docs/xnnpack_graph_check.py        # scans ./models/*.onnx
# or: python3 docs/xnnpack_graph_check.py models/real_esrgan_x4plus-single.onnx
```

The script prints, per model: opset/IR, input/output shapes, a full op-type
histogram (flagging non-XNNPACK and CPU-fallback ops), a detailed dump of every
`Resize` node (with `mode` + `coordinate_transformation_mode`), and a heuristic
verdict, plus a summary table. If run under an ONNX Runtime build that ships the
XNNPACK EP, it will also attempt a real XNNPACK session build and report node
placement (skipped gracefully otherwise — e.g. desktop CPU wheels).

> Note: the script's `XNNPACK_SUPPORTED` set is a conservative heuristic and may
> differ from your exact ORT/Android build. The **op histogram** and the
> **`Resize` mode dump** are the ground truth; the on-device node-placement log
> (§4.2) is the final authority.

## Appendix B — catalog model reference

All models are user-imported at runtime (no bundled weights, no network). Catalog
lives in `domain/model/ai/ModelCatalog.kt`:

| File | Feature | XNNPACK-eligible (`2.4.1`) |
|---|---|---|
| `real_esrgan_general_x4v3-single.onnx` | Upscale | ✅ (pending PRelu check) |
| `real_esrgan_x4plus-single.onnx` | Upscale | ✅ |
| `SCUNet-GAN.onnx` / `SCUNet-PSNR.onnx` | Enhance | ❌ |
| `GPEN-BFR-512.onnx` / `gpen_bfr_256.onnx` | Face restore | ❌ |
| `isnet-general-use-dynamic.onnx` / `u2netp.onnx` | Remove bg | ❌ (blocked) |
