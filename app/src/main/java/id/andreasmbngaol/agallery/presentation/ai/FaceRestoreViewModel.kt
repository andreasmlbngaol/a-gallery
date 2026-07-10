package id.andreasmbngaol.agallery.presentation.ai

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.FaceDetectionResult
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreOutcome
import id.andreasmbngaol.agallery.domain.model.ai.FaceRestoreSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.DetectFacesUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.RestoreFacesUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveFaceRestoreResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the Face Restore screen for a single source image. Observes which
 * face-restore models are installed, runs on-device GPEN restoration into a
 * preview, and saves that preview into the gallery as a NEW file on request.
 * The original is never modified and no network access occurs.
 *
 * Structurally mirrors [ImageUpscaleViewModel] but replaces the output-size mode
 * with a blend [strength], and measures progress in FACES instead of tiles.
 */
class FaceRestoreViewModel(
    private val sourceUri: String,
    private val sourceDisplayName: String,
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val detectFacesUseCase: DetectFacesUseCase,
    private val restoreFaces: RestoreFacesUseCase,
    private val saveResult: SaveFaceRestoreResultUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val feature = AiFeature.FACE_RESTORATION

    private val selectedModelId = MutableStateFlow<AiModelId?>(null)
    private val strength = MutableStateFlow(FaceRestoreUiState.DEFAULT_STRENGTH)
    private val resultPath = MutableStateFlow<String?>(null)
    private val processing = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    // Faces detected in the source image, drawn as bounding boxes over the
    // original preview so the user can see which faces will be restored.
    // null while detection is still running; non-null (possibly with zero
    // faces) once it finishes. Detection runs once on load and is independent
    // of the chosen model.
    private val detection = MutableStateFlow<FaceDetectionResult?>(null)

    init {
        viewModelScope.launch {
            detection.value = runCatching { detectFacesUseCase(sourceUri) }
                .getOrDefault(FaceDetectionResult.EMPTY)
        }
    }

    private var inferenceJob: Job? = null

    // Monotonic id of the current restore run. Starting or cancelling a run bumps
    // it. The native per-face loop can't be interrupted mid-face, so a cancelled
    // run may keep firing progress for a moment; gating every meter/result/flag
    // write on this token makes those late writes from an old run no-ops instead
    // of corrupting the live run.
    private val runToken = AtomicLong(0L)

    private val meter = MutableStateFlow(ProcessingMeter())

    private val messageChannel = Channel<AiUiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val statuses: StateFlow<List<ModelStatus>?> = observeModelStatus(feature)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    private val work: StateFlow<WorkState> =
        combine(processing, saving, meter) { p, s, m -> WorkState(p, s, m) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), WorkState())

    private val selection: StateFlow<Selection> =
        combine(selectedModelId, strength) { id, str -> Selection(id, str) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), Selection())

    // Preview-related state (the produced result + detected faces) folded into a
    // single flow so the top-level uiState combine stays within the 5-flow limit.
    private val preview: StateFlow<PreviewState> =
        combine(resultPath, detection) { result, det -> PreviewState(result, det) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), PreviewState())

    val uiState: StateFlow<FaceRestoreUiState> = combine(
        statuses,
        selection,
        preview,
        work,
        settings,
    ) { statusList, sel, preview, work, s ->
        val installed = statusList.orEmpty().filter { it.isInstalled }.map { it.spec }
        val effectiveId = effectiveModelId(sel.modelId, installed.map { it.id })
        val det = preview.detection
        FaceRestoreUiState(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            installedModels = installed,
            selectedModelId = effectiveId,
            strength = sel.strength,
            resultPath = preview.resultPath,
            processing = work.processing,
            saving = work.saving,
            componentStyleChosen = s.componentStyle,
            edgeEffectMode = s.edgeEffectMode,
            checkingModels = statusList == null,
            processingElapsedSeconds = work.meter.elapsedSeconds,
            processingUsedMemoryBytes = work.meter.usedMemoryBytes,
            processingCompletedFaces = work.meter.completedFaces,
            processingTotalFaces = work.meter.totalFaces,
            detectedFaces = det?.faces.orEmpty(),
            sourceWidth = det?.imageWidth ?: 0,
            sourceHeight = det?.imageHeight ?: 0,
            detectionDone = det != null,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        FaceRestoreUiState(sourceUri = sourceUri, sourceDisplayName = sourceDisplayName),
    )

    /** Choose which installed model to run; clears any stale preview. */
    fun selectModel(id: AiModelId) {
        if (selectedModelId.value == id) return
        selectedModelId.value = id
        resultPath.value = null
    }

    /** Set the blend strength (0..1); clears any stale preview. */
    fun setStrength(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (strength.value == clamped) return
        strength.value = clamped
        resultPath.value = null
    }

    /** Run face restoration with the effective model. No-op while busy. */
    fun restore() {
        if (processing.value) return
        val installedIds = statuses.value.orEmpty().filter { it.isInstalled }.map { it.spec.id }
        val modelId = effectiveModelId(selectedModelId.value, installedIds)
        if (modelId == null) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_face_restore_no_model)) }
            return
        }
        val strengthValue = strength.value
        processing.value = true
        val token = runToken.incrementAndGet()
        inferenceJob = viewModelScope.launch {
            try {
                // Fail fast if the device almost certainly lacks the RAM: heavy
                // models are killed by the OS Low-Memory-Killer (uncatchable), so
                // we warn instead of letting the app force-close mid-run.
                val spec = ModelCatalog.byId(modelId)
                if (spec != null && spec.estimatedPeakMemoryBytes > 0L) {
                    val capability = withContext(Dispatchers.Default) { deviceBenchmark.measure() }
                    val suitability = ModelSuitabilityEvaluator.evaluate(spec, capability)
                    if (suitability.rating == ModelSuitability.Rating.INSUFFICIENT_MEMORY) {
                        messageChannel.send(
                            AiUiMessage(R.string.msg_face_restore_insufficient_memory, spec.displayName),
                        )
                        return@launch
                    }
                }

                meter.value = ProcessingMeter()
                val meterJob = launch(Dispatchers.Default) {
                    val startedAt = SystemClock.elapsedRealtime()
                    while (isActive) {
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = ((now - startedAt) / 1000L).toInt()
                        val info = Debug.MemoryInfo()
                        Debug.getMemoryInfo(info)
                        meter.update { m ->
                            m.copy(
                                elapsedSeconds = elapsed,
                                usedMemoryBytes = info.totalPss.toLong() * 1024L,
                            )
                        }
                        delay(500L.milliseconds)
                    }
                }

                val outcome = try {
                    restoreFaces(sourceUri, modelId, strengthValue) { completed, total ->
                        // Fired from the inference thread as each face finishes.
                        // Ignore stray callbacks from a run that was cancelled
                        // or superseded, so they can't rewind the live counter.
                        if (runToken.get() != token) return@restoreFaces
                        meter.update { it.copy(completedFaces = completed, totalFaces = total) }
                    }
                } finally {
                    meterJob.cancel()
                    // Only the live run may reset the meter; a stale run must not
                    // wipe the meter of the run that replaced it.
                    if (runToken.get() == token) meter.value = ProcessingMeter()
                }
                if (runToken.get() == token) when (outcome) {
                    is FaceRestoreOutcome.Success -> resultPath.value = outcome.resultPath
                    is FaceRestoreOutcome.Failure -> messageChannel.send(
                        AiUiMessage(
                            when (outcome.reason) {
                                FaceRestoreOutcome.Reason.NO_MODEL -> R.string.msg_face_restore_no_model
                                FaceRestoreOutcome.Reason.SOURCE_UNREADABLE ->
                                    R.string.msg_face_restore_source_unreadable
                                FaceRestoreOutcome.Reason.NO_FACES -> R.string.msg_face_restore_no_faces
                                FaceRestoreOutcome.Reason.FAILED -> R.string.msg_face_restore_failed
                            },
                        ),
                    )
                }
            } finally {
                // A stale run finishing must not clear the busy flag of the run
                // that superseded it (which would let a third run start).
                if (runToken.get() == token) processing.value = false
            }
        }
    }

    /**
     * Cancel an in-progress run. The dialog dismisses immediately; the native
     * inference call cannot be interrupted mid-run, so it finishes in the
     * background and its (now discarded) result is ignored.
     */
    fun cancelRestore() {
        if (!processing.value) return
        // Invalidate the run first so any late progress callbacks from the
        // still-draining native loop (and its meter reset) become no-ops.
        runToken.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        processing.value = false
        meter.value = ProcessingMeter()
        viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_face_restore_cancelled)) }
    }

    /** Save the current preview into the gallery as a new file. */
    fun save() {
        val path = resultPath.value ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val outcome = saveResult(path, sourceDisplayName)
            saving.value = false
            messageChannel.send(
                when (outcome) {
                    is FaceRestoreSaveOutcome.Success ->
                        AiUiMessage(R.string.msg_face_restore_saved, outcome.displayName)
                    is FaceRestoreSaveOutcome.Failure ->
                        AiUiMessage(R.string.msg_face_restore_save_failed)
                },
            )
        }
    }

    private fun effectiveModelId(
        selected: AiModelId?,
        installedIds: List<AiModelId>,
    ): AiModelId? =
        selected?.takeIf { it in installedIds } ?: installedIds.firstOrNull()

    private data class Selection(
        val modelId: AiModelId? = null,
        val strength: Float = FaceRestoreUiState.DEFAULT_STRENGTH,
    )

    private data class ProcessingMeter(
        val elapsedSeconds: Int = 0,
        val usedMemoryBytes: Long = 0L,
        val completedFaces: Int = 0,
        val totalFaces: Int = 0,
    )

    private data class PreviewState(
        val resultPath: String? = null,
        val detection: FaceDetectionResult? = null,
    )

    private data class WorkState(
        val processing: Boolean = false,
        val saving: Boolean = false,
        val meter: ProcessingMeter = ProcessingMeter(),
    )
}
