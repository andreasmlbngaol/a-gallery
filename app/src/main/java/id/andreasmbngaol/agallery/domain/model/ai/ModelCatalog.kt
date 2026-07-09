package id.andreasmbngaol.agallery.domain.model.ai

/**
 * The built-in catalog of AI models AGallery knows how to run. This is pure
 * metadata: no weights ship with the app. Each entry tells the user where to
 * download the `.onnx` file and tells the inference engine exactly how to feed
 * images in and read masks out.
 *
 * All background-removal models are permissively licensed for commercial
 * use (Apache-2.0 / MIT). Models with non-commercial licenses (e.g. BRIA RMBG)
 * are intentionally excluded.
 *
 * The weights are mirrored on Hugging Face for reliable hosting:
 * https://huggingface.co/andreasmlbngaol/a-gallery
 *
 * Each [sha256] is pinned to the canonical artifact at its [downloadUrl], so an
 * import only succeeds when the bytes match exactly (this is what stops the
 * wrong model being imported into the wrong slot). [expectedSizeBytes] stays 0
 * because the checksum is the stronger guard, and [ModelIoSpec] tensor names are
 * left empty so the engine resolves them from the loaded graph at runtime.
 *
 * Only models with a large, DYNAMIC input set [AiModelSpec.offersQualityChoice]
 * to true; those expose the per-run Eco/Balanced/High quality selector. Small or
 * fixed-size models always run at their native resolution and hide the selector.
 */
object ModelCatalog {

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

    /** Every known model, in display order (recommended first within a feature). */
    val ALL: List<AiModelSpec> = listOf(
        ISNET_GENERAL_USE,
        U2NETP,
    )

    /** Models that power the given [feature], recommended entry first. */
    fun forFeature(feature: AiFeature): List<AiModelSpec> =
        ALL.filter { it.feature == feature }
            .sortedByDescending { it.recommended }

    /** Looks up a catalog entry by id, or null if unknown. */
    fun byId(id: AiModelId): AiModelSpec? = ALL.firstOrNull { it.id == id }

}
