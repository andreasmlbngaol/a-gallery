package id.andreasmbngaol.agallery.presentation.ai

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.EnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.EnhancePhotoUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SavePhotoEnhanceResultUseCase
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
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the Photo Enhance screen for a single source image. Observes which
 * enhance models are installed, runs on-device SCUNet restoration into a
 * preview, and saves that preview into the gallery as a NEW file on request.
 * The original is never modified and no network access occurs.
 *
 * Combines the two other single-image AI ViewModels: like [ImageUpscaleViewModel]
 * it measures progress in TILES, and like [FaceRestoreViewModel] it exposes a
 * blend [strength] instead of an output-size mode (Enhance keeps the original
 * resolution). Unlike Face Restore there is no detection pass, so the result
 * is folded straight into the work state.
 */
class PhotoEnhanceViewModel(
    private val sourceUri: String,
    private val sourceDisplayName: String,
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val enhancePhoto: EnhancePhotoUseCase,
    private val saveResult: SavePhotoEnhanceResultUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val feature = AiFeature.IMAGE_ENHANCE

    private val selectedModelId = MutableStateFlow<AiModelId?>(null)
    private val strength = MutableStateFlow(PhotoEnhanceUiState.DEFAULT_STRENGTH)
    private val resultPath = MutableStateFlow<String?>(null)
    private val processing = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    private var inferenceJob: Job? = null

    // Monotonic id of the current enhance run. Starting or cancelling a run bumps
    // it. The native tile loop can't be interrupted mid-tile, so a cancelled run
    // may keep firing progress for a moment; gating every meter/result/flag write
    // on this token makes those late writes from an old run no-ops instead of
    // corrupting the live run.
    private val runToken = AtomicLong(0L)

    private val meter = MutableStateFlow(ProcessingMeter())

    private val messageChannel = Channel<AiUiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val statuses: StateFlow<List<ModelStatus>?> = observeModelStatus(feature)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    // The produced result is folded into the work state (rather than kept as its
    // own top-level flow) so the uiState combine stays within the 5-flow limit.
    private val work: StateFlow<WorkState> =
        combine(processing, saving, meter, resultPath) { p, s, m, result ->
            WorkState(p, s, m, result)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), WorkState())

    private val selection: StateFlow<Selection> =
        combine(selectedModelId, strength) { id, str -> Selection(id, str) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), Selection())

    val uiState: StateFlow<PhotoEnhanceUiState> = combine(
        statuses,
        selection,
        work,
        settings,
    ) { statusList, sel, work, s ->
        val installed = statusList.orEmpty().filter { it.isInstalled }.map { it.spec }
        val effectiveId = effectiveModelId(sel.modelId, installed.map { it.id })
        PhotoEnhanceUiState(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            installedModels = installed,
            selectedModelId = effectiveId,
            strength = sel.strength,
            resultPath = work.resultPath,
            processing = work.processing,
            saving = work.saving,
            componentStyleChosen = s.componentStyle,
            edgeEffectMode = s.edgeEffectMode,
            checkingModels = statusList == null,
            processingElapsedSeconds = work.meter.elapsedSeconds,
            processingUsedMemoryBytes = work.meter.usedMemoryBytes,
            processingCompletedTiles = work.meter.completedTiles,
            processingTotalTiles = work.meter.totalTiles,
            processingEtaSeconds = work.meter.etaSeconds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        PhotoEnhanceUiState(sourceUri = sourceUri, sourceDisplayName = sourceDisplayName),
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

    /** Run enhancement with the effective model. No-op while busy. */
    fun enhance() {
        if (processing.value) return
        val installedIds = statuses.value.orEmpty().filter { it.isInstalled }.map { it.spec.id }
        val modelId = effectiveModelId(selectedModelId.value, installedIds)
        if (modelId == null) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_enhance_no_model)) }
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
                            AiUiMessage(R.string.msg_enhance_insufficient_memory, spec.displayName),
                        )
                        return@launch
                    }
                }

                meter.value = ProcessingMeter()
                // Wall-clock at which the FIRST tile started (after decode +
                // model load) and at which the most recently finished tile
                // landed; together they let the ETA count DOWN smoothly in real
                // time instead of only stepping when a tile completes. The
                // startup cost is excluded so the estimate is pure per-tile time.
                val tilesStartedAt = AtomicLong(0L)
                val lastTileCompletedAt = AtomicLong(0L)
                val meterJob = launch(Dispatchers.Default) {
                    val startedAt = SystemClock.elapsedRealtime()
                    while (isActive) {
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = ((now - startedAt) / 1000L).toInt()
                        val info = Debug.MemoryInfo()
                        Debug.getMemoryInfo(info)
                        val start = tilesStartedAt.get()
                        val lastAt = lastTileCompletedAt.get()
                        meter.update { m ->
                            // ETA = average time per finished tile * tiles left,
                            // MINUS how long the current tile has already run, so
                            // the estimate shrinks every tick and only
                            // re-baselines when another tile lands.
                            val eta = if (m.completedTiles > 0 && m.totalTiles > 0 && start > 0L && lastAt > start) {
                                val avgPerTileMs = (lastAt - start).toDouble() / m.completedTiles
                                val remaining = (m.totalTiles - m.completedTiles).coerceAtLeast(0)
                                val sinceLastMs = (now - lastAt).toDouble()
                                val remainingMs = (avgPerTileMs * remaining - sinceLastMs).coerceAtLeast(0.0)
                                ceil(remainingMs / 1000.0).toInt()
                            } else {
                                -1
                            }
                            m.copy(
                                elapsedSeconds = elapsed,
                                usedMemoryBytes = info.totalPss.toLong() * 1024L,
                                etaSeconds = eta,
                            )
                        }
                        delay(500L.milliseconds)
                    }
                }

                val outcome = try {
                    enhancePhoto(sourceUri, modelId, strengthValue) { completed, total ->
                        // Fired from the inference thread as each tile finishes.
                        // The first callback (completed == 0) marks the tile-loop
                        // start; every later one stamps the moment a tile landed
                        // so the meter above can run a real countdown.
                        val now = SystemClock.elapsedRealtime()
                        if (completed == 0) {
                            tilesStartedAt.set(now)
                            lastTileCompletedAt.set(0L)
                        } else {
                            lastTileCompletedAt.set(now)
                        }
                        // Ignore stray callbacks from a run that was cancelled
                        // or superseded, so they can't rewind the live counter.
                        if (runToken.get() != token) return@enhancePhoto
                        meter.update { it.copy(completedTiles = completed, totalTiles = total) }
                    }
                } finally {
                    meterJob.cancel()
                    // Only the live run may reset the meter; a stale run must not
                    // wipe the meter of the run that replaced it.
                    if (runToken.get() == token) meter.value = ProcessingMeter()
                }
                if (runToken.get() == token) when (outcome) {
                    is EnhanceOutcome.Success -> resultPath.value = outcome.resultPath
                    is EnhanceOutcome.Failure -> messageChannel.send(
                        AiUiMessage(
                            when (outcome.reason) {
                                EnhanceOutcome.Reason.NO_MODEL -> R.string.msg_enhance_no_model
                                EnhanceOutcome.Reason.SOURCE_UNREADABLE ->
                                    R.string.msg_enhance_source_unreadable
                                EnhanceOutcome.Reason.FAILED -> R.string.msg_enhance_failed
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
    fun cancelEnhance() {
        if (!processing.value) return
        // Invalidate the run first so any late progress callbacks from the
        // still-draining native loop (and its meter reset) become no-ops.
        runToken.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        processing.value = false
        meter.value = ProcessingMeter()
        viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_enhance_cancelled)) }
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
                    is EnhanceSaveOutcome.Success ->
                        AiUiMessage(R.string.msg_enhance_saved, outcome.displayName)
                    is EnhanceSaveOutcome.Failure ->
                        AiUiMessage(R.string.msg_enhance_save_failed)
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
        val strength: Float = PhotoEnhanceUiState.DEFAULT_STRENGTH,
    )

    private data class ProcessingMeter(
        val elapsedSeconds: Int = 0,
        val usedMemoryBytes: Long = 0L,
        val completedTiles: Int = 0,
        val totalTiles: Int = 0,
        val etaSeconds: Int = -1,
    )

    private data class WorkState(
        val processing: Boolean = false,
        val saving: Boolean = false,
        val meter: ProcessingMeter = ProcessingMeter(),
        val resultPath: String? = null,
    )
}
