package id.andreasmbngaol.agallery.domain.model.ai

/**
 * The built-in catalog of AI models AGallery knows how to run. This is pure
 * metadata: no weights ship with the app. Each entry tells the user where to
 * download the `.onnx` file and tells the inference engine exactly how to feed
 * images in and read the result out.
 *
 * AGallery itself ships under the PolyForm Noncommercial 1.0.0 license, so the
 * catalog is free to reference the best NON-COMMERCIAL research weights (e.g.
 * GPEN for face restoration) alongside permissive ones — commercial
 * redistribution is out of scope by design. No weights ship with the app; the
 * user downloads each `.onnx` themselves, so the ultimate license choice still
 * rests with them.
 *
 * Each [sha256] is pinned to the canonical artifact, so an import only succeeds
 * when the bytes match exactly (this is what stops the wrong model being
 * imported into the wrong slot). [expectedSizeBytes] stays 0 because the
 * checksum is the stronger guard, and [ModelIoSpec] tensor names are left empty
 * so the engine resolves them from the loaded graph at runtime.
 *
 * Only models with a large, DYNAMIC input set [AiModelSpec.offersQualityChoice]
 * to true; those expose the per-run Eco/Balanced/High quality selector. Small or
 * fixed-size models always run at their native resolution and hide the selector.
 */
object ModelCatalog {

    // ------------------------------------------------------------------
    // Background removal (2.1.x)
    // ------------------------------------------------------------------

    /**
     * IS-Net (General Use) — the balanced, recommended default. This is the
     * DYNAMIC re-export, so it exposes the Eco/Balanced/High quality selector.
     */
    val ISNET_GENERAL_USE: AiModelSpec = AiModelSpec(
        id = AiModelId("isnet-general-use"),
        feature = AiFeature.BACKGROUND_REMOVAL,
        tier = ModelTier.BALANCED,
        displayName = "IS-Net (General Use)",
        approxSizeBytes = 179L * 1024 * 1024,
        expectedSizeBytes = 0L,
        sha256 = "3445af39ebc8cd513db5c3724837329ba122c361cbcb0fa5b4cc803115eca899",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 1024,
            inputHeight = 1024,
            layout = TensorLayout.NCHW,
            mean = listOf(0.5f, 0.5f, 0.5f),
            std = listOf(1.0f, 1.0f, 1.0f),
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/isnet-general-use-dynamic.onnx?download=true",
        recommended = true,
        offersQualityChoice = true,
        estimatedPeakMemoryBytes = 1_100L * 1024 * 1024,
    )

    /** U²-Net (Lite) — tiny & fast, lowest fidelity. Fixed 320² input. */
    val U2NETP: AiModelSpec = AiModelSpec(
        id = AiModelId("u2netp"),
        feature = AiFeature.BACKGROUND_REMOVAL,
        tier = ModelTier.LIGHT,
        displayName = "U²-Net (Lite)",
        approxSizeBytes = 4_792_000L,
        expectedSizeBytes = 0L,
        sha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 320,
            inputHeight = 320,
            layout = TensorLayout.NCHW,
            mean = listOf(0.485f, 0.456f, 0.406f),
            std = listOf(0.229f, 0.224f, 0.225f),
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/u2netp.onnx?download=true",
        recommended = false,
        estimatedPeakMemoryBytes = 300L * 1024 * 1024,
    )

    // ------------------------------------------------------------------
    // Image upscaling (2.2.0)
    //
    // All three are 4x super-resolution models with a FIXED 128x128 square
    // input (scaleFactor = 4 -> 512x512 output per tile), fed RGB in 0..1 with
    // no mean/std shift (mean 0 / std 1). They run tiled across the whole photo.
    // They output a full RGB image, not a mask, so offersQualityChoice = false
    // and the upscale processor reassembles tiles rather than compositing alpha.
    //
    // The .onnx files are self-contained single-file exports (weights embedded);
    // the pinned sha256 is of that merged file, NOT the external-data graph stub.
    // ------------------------------------------------------------------

    /**
     * Real-ESRGAN General x4v3 (Eco) — smallest & fastest upscaler, great for
     * general photos on low/mid-range devices. Apache-2.0 (Qualcomm AI Hub).
     */
    val REAL_ESRGAN_GENERAL_X4V3: AiModelSpec = AiModelSpec(
        id = AiModelId("real-esrgan-general-x4v3"),
        feature = AiFeature.IMAGE_UPSCALE,
        tier = ModelTier.LIGHT,
        displayName = "Real-ESRGAN General x4 (v3)",
        approxSizeBytes = 4_882_000L,
        expectedSizeBytes = 0L,
        sha256 = "a86302db1b67bd6e92e7238ece29ac0cb58a53ef42be51d0f80f798d77579a00",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 128,
            inputHeight = 128,
            layout = TensorLayout.NCHW,
            mean = listOf(0f, 0f, 0f),
            std = listOf(1f, 1f, 1f),
            scaleFactor = 4,
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/real_esrgan_general_x4v3-single.onnx?download=true",
        recommended = false,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 250L * 1024 * 1024,
    )

    /**
     * Real-ESRGAN x4plus (Balanced) — the recommended default upscaler; strong
     * detail recovery at a moderate size. Apache-2.0 (Qualcomm AI Hub).
     */
    val REAL_ESRGAN_X4PLUS: AiModelSpec = AiModelSpec(
        id = AiModelId("real-esrgan-x4plus"),
        feature = AiFeature.IMAGE_UPSCALE,
        tier = ModelTier.BALANCED,
        displayName = "Real-ESRGAN x4plus",
        approxSizeBytes = 67_160_000L,
        expectedSizeBytes = 0L,
        sha256 = "1cdf2d4934e2c3105f0082dbd482883d8b0a600c64803e72ee27fa94454b8651",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 128,
            inputHeight = 128,
            layout = TensorLayout.NCHW,
            mean = listOf(0f, 0f, 0f),
            std = listOf(1f, 1f, 1f),
            scaleFactor = 4,
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/real_esrgan_x4plus-single.onnx?download=true",
        recommended = true,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 400L * 1024 * 1024,
    )

    // ------------------------------------------------------------------
    // Face restoration (2.3.0)
    //
    // Blind face-restoration GPEN models. Each takes a single ALIGNED, SQUARE
    // face crop at its native resolution (256 or 512) as NCHW RGB normalized to
    // [-1, 1] (mean 0.5 / std 0.5) and outputs the restored face in the SAME
    // [-1, 1] range (de-normalized by the face-restore processor, not the shared
    // 0..1 upscale reader). scaleFactor stays 1 (same-size output). The photo's
    // faces are detected on-device (ML Kit, bundled) and each is restored and
    // feather-blended back in; non-face areas are untouched. Fixed input size, so
    // offersQualityChoice = false — instead the UI exposes a blend-strength
    // slider. GPEN is research-only (non-commercial), which AGallery's license
    // allows.
    // ------------------------------------------------------------------

    /**
     * GPEN-BFR 256 (Light) — the fast, low-memory face restorer; great on
     * mid-range devices. Restores at 256² per face. Non-commercial (GPEN).
     */
    val GPEN_BFR_256: AiModelSpec = AiModelSpec(
        id = AiModelId("gpen-bfr-256"),
        feature = AiFeature.FACE_RESTORATION,
        tier = ModelTier.LIGHT,
        displayName = "GPEN-BFR 256",
        approxSizeBytes = 75_800_000L,
        expectedSizeBytes = 0L,
        sha256 = "bad8bf0426873828df2dbf4e3b3d9ababba9da7965b8b72426569486f7ae5c25",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 256,
            inputHeight = 256,
            layout = TensorLayout.NCHW,
            mean = listOf(0.5f, 0.5f, 0.5f),
            std = listOf(0.5f, 0.5f, 0.5f),
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/gpen_bfr_256.onnx?download=true",
        recommended = false,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 600L * 1024 * 1024,
    )

    /**
     * GPEN-BFR 512 (Balanced) — the recommended default face restorer; sharper,
     * higher-fidelity results at 512² per face for a moderate memory cost.
     * Non-commercial (GPEN).
     */
    val GPEN_BFR_512: AiModelSpec = AiModelSpec(
        id = AiModelId("gpen-bfr-512"),
        feature = AiFeature.FACE_RESTORATION,
        tier = ModelTier.BALANCED,
        displayName = "GPEN-BFR 512",
        approxSizeBytes = 284_000_000L,
        expectedSizeBytes = 0L,
        sha256 = "bf80acb8e91ba8852e3f012505be2c3b6cd6b3eed5ec605e3db87863c4e74d4e",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 512,
            inputHeight = 512,
            layout = TensorLayout.NCHW,
            mean = listOf(0.5f, 0.5f, 0.5f),
            std = listOf(0.5f, 0.5f, 0.5f),
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/GPEN-BFR-512.onnx?download=true",
        recommended = true,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 1_600L * 1024 * 1024,
    )

    // ------------------------------------------------------------------
    // Photo enhancement (2.4.0)
    //
    // Whole-image restoration with SCUNet (Swin-Conv-UNet blind denoiser). Both
    // models are fed RGB in 0..1 with NO mean/std shift (mean 0 / std 1) and
    // output a cleaned RGB image the SAME size as the input (scaleFactor = 1),
    // read back by the shared 0..1 reader (NOT the signed face-restore reader).
    // SCUNet is fully-convolutional; the enhance processor runs it tiled across
    // the whole photo at a fixed 256x256 window (a multiple of the network's
    // downsampling factor) and blends the result over the original by strength.
    //
    // The two models are a STYLE choice, NOT a speed tier (both are BALANCED and
    // the same size): SCUNet-GAN restores sharper, more detailed texture, while
    // SCUNet-PSNR is cleaner and smoother. The UI describes them as Sharp/Clean
    // rather than by tier. SCUNet is Apache-2.0 (cszn); the ONNX exports are the
    // dynamic-input builds from deepghs/image_restoration.
    // ------------------------------------------------------------------

    /**
     * SCUNet-GAN (Sharp) — the recommended default enhancer: restores crisp,
     * detailed texture with a light, natural sharpening. Apache-2.0 (cszn).
     */
    val SCUNET_GAN: AiModelSpec = AiModelSpec(
        id = AiModelId("scunet-gan"),
        feature = AiFeature.IMAGE_ENHANCE,
        tier = ModelTier.BALANCED,
        displayName = "SCUNet (Sharp)",
        approxSizeBytes = 87L * 1024 * 1024,
        expectedSizeBytes = 0L,
        sha256 = "79ae6073c91c2d25d1f199137a67c8d0f0807df27219cdd7d890f3cc6d5b43e7",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 256,
            inputHeight = 256,
            layout = TensorLayout.NCHW,
            mean = listOf(0f, 0f, 0f),
            std = listOf(1f, 1f, 1f),
            scaleFactor = 1,
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/SCUNet-GAN.onnx?download=true",
        recommended = true,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 700L * 1024 * 1024,
    )

    /**
     * SCUNet-PSNR (Clean) — the smooth, low-noise alternative: maximises
     * clean-up (highest PSNR) for a gentler, softer look. Apache-2.0 (cszn).
     */
    val SCUNET_PSNR: AiModelSpec = AiModelSpec(
        id = AiModelId("scunet-psnr"),
        feature = AiFeature.IMAGE_ENHANCE,
        tier = ModelTier.BALANCED,
        displayName = "SCUNet (Clean)",
        approxSizeBytes = 87L * 1024 * 1024,
        expectedSizeBytes = 0L,
        sha256 = "b0f8c12f1575bb49e39a85924152f1c6d4b527a4aae0432c9e5c7397123465e3",
        io = ModelIoSpec(
            inputName = "",
            outputName = "",
            inputWidth = 256,
            inputHeight = 256,
            layout = TensorLayout.NCHW,
            mean = listOf(0f, 0f, 0f),
            std = listOf(1f, 1f, 1f),
            scaleFactor = 1,
        ),
        downloadUrl = "https://huggingface.co/andreasmlbngaol/a-gallery/resolve/main/SCUNet-PSNR.onnx?download=true",
        recommended = false,
        offersQualityChoice = false,
        estimatedPeakMemoryBytes = 700L * 1024 * 1024,
    )

    /** Every known model, in display order (recommended first within a feature). */
    val ALL: List<AiModelSpec> = listOf(
        ISNET_GENERAL_USE,
        U2NETP,
        REAL_ESRGAN_X4PLUS,
        REAL_ESRGAN_GENERAL_X4V3,
        GPEN_BFR_512,
        GPEN_BFR_256,
        SCUNET_GAN,
        SCUNET_PSNR,
    )

    /** Models that power the given [feature], recommended entry first. */
    fun forFeature(feature: AiFeature): List<AiModelSpec> =
        ALL.filter { it.feature == feature }
            .sortedByDescending { it.recommended }

    /** Looks up a catalog entry by id, or null if unknown. */
    fun byId(id: AiModelId): AiModelSpec? = ALL.firstOrNull { it.id == id }

}
