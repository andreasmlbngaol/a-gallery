package id.andreasmbngaol.agallery.presentation.ai

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.RemoveBackgroundUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveBackgroundResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the Background Remover screen for a single source image. Observes which
 * models are installed (so it can offer a choice or prompt the user to install
 * one), runs on-device removal into a transparent preview, and saves that
 * preview into the gallery as a NEW file on request. The original is never
 * modified and no network access occurs.
 *
 * [sourceUri] and [sourceDisplayName] are provided as Koin parameters from the
 * navigation route.
 */
class BackgroundRemoverViewModel(
    private val sourceUri: String,
    private val sourceDisplayName: String,
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val removeBackground: RemoveBackgroundUseCase,
    private val saveResult: SaveBackgroundResultUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val feature = AiFeature.BACKGROUND_REMOVAL

    private val selectedModelId = MutableStateFlow<AiModelId?>(null)
    private val resultPath = MutableStateFlow<String?>(null)
    private val processing = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    /** Live inference meter (elapsed time + process memory), updated while running. */
    private val meter = MutableStateFlow(ProcessingMeter())

    private val messageChannel = Channel<AiUiMessage>(Channel.BUFFERED)

    /** One-shot messages (removal/save results) for the screen to surface. */
    val messages = messageChannel.receiveAsFlow()

    private val statuses: StateFlow<List<ModelStatus>?> = observeModelStatus(feature)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    private val work: StateFlow<WorkState> =
        combine(processing, saving, meter) { p, s, m -> WorkState(p, s, m) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), WorkState())

    val uiState: StateFlow<BackgroundRemoverUiState> = combine(
        statuses,
        selectedModelId,
        resultPath,
        work,
        settings,
    ) { statusList, selected, result, work, s ->
        val installed = statusList.orEmpty().filter { it.isInstalled }.map { it.spec }
        BackgroundRemoverUiState(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            installedModels = installed,
            selectedModelId = effectiveModelId(selected, installed.map { it.id }),
            resultPath = result,
            processing = work.processing,
            saving = work.saving,
            componentStyleChosen = s.componentStyle,
            edgeEffectMode = s.edgeEffectMode,
            checkingModels = statusList == null,
            processingElapsedSeconds = work.meter.elapsedSeconds,
            processingUsedMemoryBytes = work.meter.usedMemoryBytes,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        BackgroundRemoverUiState(sourceUri = sourceUri, sourceDisplayName = sourceDisplayName),
    )

    /** Choose which installed model to run; clears any stale preview. */
    fun selectModel(id: AiModelId) {
        if (selectedModelId.value == id) return
        selectedModelId.value = id
        resultPath.value = null
    }

    /** Run background removal with the effective model. No-op while busy. */
    fun removeBackground() {
        if (processing.value) return
        val installedIds = statuses.value.orEmpty().filter { it.isInstalled }.map { it.spec.id }
        val modelId = effectiveModelId(selectedModelId.value, installedIds)
        if (modelId == null) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_bg_no_model)) }
            return
        }
        processing.value = true
        viewModelScope.launch {
            // Guard: never even start a run the device almost certainly cannot
            // finish. Heavy models are killed by the OS Low-Memory-Killer (an
            // uncatchable process death), so we fail fast with a clear message
            // instead of letting the app force-close mid-inference.
            val spec = ModelCatalog.byId(modelId)
            if (spec != null && spec.estimatedPeakMemoryBytes > 0L) {
                val capability = withContext(Dispatchers.Default) { deviceBenchmark.measure() }
                val suitability = ModelSuitabilityEvaluator.evaluate(spec, capability)
                if (suitability.rating == ModelSuitability.Rating.INSUFFICIENT_MEMORY) {
                    processing.value = false
                    messageChannel.send(
                        AiUiMessage(R.string.msg_bg_insufficient_memory, spec.displayName),
                    )
                    return@launch
                }
            }

            meter.value = ProcessingMeter()
            val meterJob = launch(Dispatchers.Default) {
                val startedAt = SystemClock.elapsedRealtime()
                while (isActive) {
                    val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
                    val info = Debug.MemoryInfo()
                    Debug.getMemoryInfo(info)
                    meter.value = ProcessingMeter(elapsed, info.totalPss.toLong() * 1024L)
                    delay(500L.milliseconds)
                }
            }

            val outcome = try {
                removeBackground(sourceUri, modelId)
            } finally {
                meterJob.cancel()
                meter.value = ProcessingMeter()
            }
            processing.value = false
            when (outcome) {
                is BackgroundRemovalOutcome.Success -> resultPath.value = outcome.resultPath
                is BackgroundRemovalOutcome.Failure -> messageChannel.send(
                    AiUiMessage(
                        when (outcome.reason) {
                            BackgroundRemovalOutcome.Reason.NO_MODEL -> R.string.msg_bg_no_model
                            BackgroundRemovalOutcome.Reason.SOURCE_UNREADABLE ->
                                R.string.msg_bg_source_unreadable
                            BackgroundRemovalOutcome.Reason.FAILED -> R.string.msg_bg_failed
                        },
                    ),
                )
            }
        }
    }

    /** Save the current transparent preview into the gallery as a new file. */
    fun save() {
        val path = resultPath.value ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val outcome = saveResult(path, sourceDisplayName)
            saving.value = false
            messageChannel.send(
                when (outcome) {
                    is BackgroundSaveOutcome.Success ->
                        AiUiMessage(R.string.msg_bg_saved, outcome.displayName)
                    is BackgroundSaveOutcome.Failure ->
                        AiUiMessage(R.string.msg_bg_save_failed)
                },
            )
        }
    }

    /**
     * Resolves the model to run: the user's pick if it is installed, otherwise
     * the first installed model (recommended first, per catalog order).
     */
    private fun effectiveModelId(
        selected: AiModelId?,
        installedIds: List<AiModelId>,
    ): AiModelId? =
        selected?.takeIf { it in installedIds } ?: installedIds.firstOrNull()

    /** Live progress meter shown in the processing dialog. */
    private data class ProcessingMeter(
        val elapsedSeconds: Int = 0,
        val usedMemoryBytes: Long = 0L,
    )

    /** Combined "busy" snapshot: inference/saving flags plus the live meter. */
    private data class WorkState(
        val processing: Boolean = false,
        val saving: Boolean = false,
        val meter: ProcessingMeter = ProcessingMeter(),
    )
}
