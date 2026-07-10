package id.andreasmbngaol.agallery.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.DeviceCapability
import id.andreasmbngaol.agallery.domain.model.ai.ImportOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ImportPhase
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.DeleteModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ImportModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetLiftModelUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetLiftQualityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the AI models management screen: observes each catalog model's install
 * state, runs at most one import at a time (reporting its phase), and deletes
 * installed models. All user-facing outcomes are emitted as [AiUiMessage]s for
 * the screen to show; nothing here touches Android UI or the network.
 */
class AiModelsViewModel(
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val importModel: ImportModelUseCase,
    private val deleteModel: DeleteModelUseCase,
    private val setLiftModel: SetLiftModelUseCase,
    private val setLiftQuality: SetLiftQualityUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val feature = AiFeature.BACKGROUND_REMOVAL

    private val importState = MutableStateFlow(AiImportUiState())

    /** Device benchmark result; null until the one-off measurement completes. */
    private val capability = MutableStateFlow<DeviceCapability?>(null)

    init {
        // A tiny RAM + CPU probe so each model row can advise whether this
        // device can run it. Runs once, off the main thread.
        viewModelScope.launch {
            capability.value = withContext(Dispatchers.Default) { deviceBenchmark.measure() }
        }
    }

    private val messageChannel = Channel<AiUiMessage>(Channel.BUFFERED)

    /** One-shot messages (import/delete results) for the screen to surface. */
    val messages = messageChannel.receiveAsFlow()

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    // The per-feature install-state flows, pre-combined into one flow so the main
    // uiState combine stays within kotlinx's 5-flow typed overload.
    private val featureStatuses = combine(
        observeModelStatus(feature),
        observeModelStatus(AiFeature.IMAGE_UPSCALE),
        observeModelStatus(AiFeature.FACE_RESTORATION),
    ) { bg, upscale, face -> Triple(bg, upscale, face) }

    val uiState: StateFlow<AiModelsUiState> = combine(
        featureStatuses,
        importState,
        settings,
        capability,
    ) { (statuses, upscaleStatuses, faceStatuses), import, s, cap ->
        AiModelsUiState(
            rows = statuses.toRows(import, cap),
            upscaleRows = upscaleStatuses.toRows(import, cap),
            faceRestoreRows = faceStatuses.toRows(import, cap),
            componentStyleChosen = s.componentStyle,
            edgeEffectMode = s.edgeEffectMode,
            deviceCapability = cap,
            liftModelId = s.liftModelId,
            liftQuality = s.liftQuality,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        AiModelsUiState(),
    )

    /** Joins each catalog [ModelStatus] with its live import/suitability state. */
    private fun List<ModelStatus>.toRows(
        import: AiImportUiState,
        cap: DeviceCapability?,
    ): List<AiModelRow> = map { status ->
        val importingThis = import.modelId == status.spec.id
        AiModelRow(
            spec = status.spec,
            isInstalled = status.isInstalled,
            installedSizeBytes = status.installed?.sizeBytes,
            isImporting = importingThis,
            importPhase = if (importingThis) import.phase else null,
            suitability = cap?.let {
                ModelSuitabilityEvaluator.evaluate(status.spec, it)
            },
        )
    }

    /**
     * Import the user-picked file at [sourceUri] as [spec]. Ignored if another
     * import is already running.
     */
    fun import(spec: AiModelSpec, sourceUri: String) {
        if (importState.value.isImporting) return
        importState.value = AiImportUiState(spec.id, ImportPhase.COPYING)
        viewModelScope.launch {
            val outcome = importModel(spec, sourceUri) { phase ->
                importState.update { it.copy(phase = phase) }
            }
            importState.value = AiImportUiState()
            messageChannel.send(importMessage(outcome, spec))
        }
    }

    /**
     * Persist the model used by the viewer's long-press "lift subject".
     * Pass null for Auto (smallest installed model).
     */
    fun selectLiftModel(id: String?) {
        viewModelScope.launch {
            setLiftModel(id?.let { AiModelId(it) })
        }
    }

    /** Persist the Eco/Balanced/High quality used by the lift gesture. */
    fun selectLiftQuality(quality: RemovalQuality) {
        viewModelScope.launch { setLiftQuality(quality) }
    }

    /** Delete the installed file for [spec]. */
    fun delete(spec: AiModelSpec) {
        viewModelScope.launch {
            val removed = deleteModel(spec.id)
            if (removed) {
                messageChannel.send(AiUiMessage(R.string.msg_model_deleted, spec.displayName))
            }
        }
    }

    private fun importMessage(outcome: ImportOutcome, spec: AiModelSpec): AiUiMessage =
        when (outcome) {
            is ImportOutcome.Success ->
                AiUiMessage(R.string.msg_model_imported, spec.displayName)

            is ImportOutcome.Failure -> AiUiMessage(
                when (outcome.reason) {
                    ImportOutcome.Reason.COPY_FAILED -> R.string.msg_model_import_copy_failed
                    ImportOutcome.Reason.SIZE_MISMATCH -> R.string.msg_model_import_size
                    ImportOutcome.Reason.CHECKSUM_MISMATCH -> R.string.msg_model_import_checksum
                    ImportOutcome.Reason.INVALID_MODEL -> R.string.msg_model_import_invalid
                    ImportOutcome.Reason.INFERENCE_FAILED -> R.string.msg_model_import_inference
                },
            )
        }
}
