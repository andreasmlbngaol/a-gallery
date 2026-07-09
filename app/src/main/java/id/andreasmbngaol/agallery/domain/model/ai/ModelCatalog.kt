package id.andreasmbngaol.agallery.domain.model.ai

/**
 * The built-in catalog of AI models AGallery knows how to run. This is pure
 * metadata: no weights ship with the app. Each entry tells the user where to
 * download the `.onnx` file and tells the inference engine exactly how to feed
 * images in and read the result out.
 *
 * All bundled models are permissively licensed for commercial use
 * (Apache-2.0 / MIT / BSD). Models with non-commercial licenses (e.g. BRIA RMBG
 * for background removal, or CodeFormer for face restoration) are intentionally
 * excluded so the user can ship freely; because models are imported by the user
 * anyway, license choice ultimately rests with them.
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

    /** Every known model, in display order (recommended first within a feature). */
    val ALL: List<AiModelSpec> = listOf(
        ISNET_GENERAL_USE,
        U2NETP,
        REAL_ESRGAN_X4PLUS,
        REAL_ESRGAN_GENERAL_X4V3,
    )

    /** Models that power the given [feature], recommended entry first. */
    fun forFeature(feature: AiFeature): List<AiModelSpec> =
        ALL.filter { it.feature == feature }
            .sortedByDescending { it.recommended }

    /** Looks up a catalog entry by id, or null if unknown. */
    fun byId(id: AiModelId): AiModelSpec? = ALL.firstOrNull { it.id == id }

}
