package id.andreasmbngaol.agallery.presentation.ai

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelId
import id.andreasmbngaol.agallery.domain.model.ai.ModelCatalog
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleMode
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleOutcome
import id.andreasmbngaol.agallery.domain.model.ai.UpscaleSaveOutcome
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveUpscaleResultUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.UpscaleImageUseCase
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
 * Drives the Image Upscaler screen for a single source image. Observes which
 * upscale models are installed, runs on-device 4x super-resolution into a
 * preview, and saves that preview into the gallery as a NEW file on request.
 * The original is never modified and no network access occurs.
 *
 * Structurally mirrors [BackgroundRemoverViewModel] but drops the quality
 * selector (upscale models are fixed-input).
 */
class ImageUpscaleViewModel(
    private val sourceUri: String,
    private val sourceDisplayName: String,
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val upscaleImage: UpscaleImageUseCase,
    private val saveResult: SaveUpscaleResultUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val feature = AiFeature.IMAGE_UPSCALE

    private val selectedModelId = MutableStateFlow<AiModelId?>(null)
    private val selectedMode = MutableStateFlow(UpscaleMode.AUTO)
    private val selectedStrength = MutableStateFlow(ImageUpscaleUiState.DEFAULT_STRENGTH)
    private val resultPath = MutableStateFlow<String?>(null)
    private val processing = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    private var inferenceJob: Job? = null

    // Monotonic id of the current upscale run. Starting or cancelling a run bumps
    // it. The native tile loop can't be interrupted mid-tile, so a cancelled run
    // may keep firing progress for a moment; gating every meter/result/flag write
    // on this token makes those late writes from an old run no-ops instead of
    // corrupting the live run (which caused 84 -> 6 -> 85 and finishing -> 11).
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
        combine(selectedModelId, selectedMode, selectedStrength) { id, mode, strength -> Selection(id, mode, strength) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), Selection())

    val uiState: StateFlow<ImageUpscaleUiState> = combine(
        statuses,
        selection,
        resultPath,
        work,
        settings,
    ) { statusList, sel, result, work, s ->
        val installed = statusList.orEmpty().filter { it.isInstalled }.map { it.spec }
        val effectiveId = effectiveModelId(sel.modelId, installed.map { it.id })
        ImageUpscaleUiState(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            installedModels = installed,
            selectedModelId = effectiveId,
            selectedMode = sel.mode,
            strength = sel.strength,
            resultPath = result,
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
        ImageUpscaleUiState(sourceUri = sourceUri, sourceDisplayName = sourceDisplayName),
    )

    /** Choose which installed model to run; clears any stale preview. */
    fun selectModel(id: AiModelId) {
        if (selectedModelId.value == id) return
        selectedModelId.value = id
        resultPath.value = null
    }

    /** Choose the output-size mode; clears any stale preview. */
    fun selectMode(mode: UpscaleMode) {
        if (selectedMode.value == mode) return
        selectedMode.value = mode
        resultPath.value = null
    }

    /** Set the blend strength (0..1) of AI over a plain resize; clears any stale preview. */
    fun setStrength(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (selectedStrength.value == clamped) return
        selectedStrength.value = clamped
        resultPath.value = null
    }

    /** Run upscaling with the effective model. No-op while busy. */
    fun upscale() {
        if (processing.value) return
        val installedIds = statuses.value.orEmpty().filter { it.isInstalled }.map { it.spec.id }
        val modelId = effectiveModelId(selectedModelId.value, installedIds)
        if (modelId == null) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_upscale_no_model)) }
            return
        }
        val mode = selectedMode.value
        val strength = selectedStrength.value
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
                            AiUiMessage(R.string.msg_upscale_insufficient_memory, spec.displayName),
                        )
                        return@launch
                    }
                }

                meter.value = ProcessingMeter()
                // Wall-clock at which the FIRST tile started (i.e. after decode +
                // session build), so the ETA is based on pure per-tile time and
                // isn't skewed by the one-off startup cost.
                val tilesStartedAt = AtomicLong(0L)
                // Wall-clock at which the most recently finished tile completed.
                // Combined with [tilesStartedAt] this lets the ETA count DOWN in
                // real time between completions instead of only stepping down when
                // a tile finishes.
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
                            // ETA counts down smoothly: it is the average time per
                            // finished tile times the tiles left, MINUS however long
                            // the current tile has already been running. That second
                            // term grows every tick, so the estimate shrinks in real
                            // time (and only re-baselines when another tile lands).
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
                    upscaleImage(sourceUri, modelId, mode, strength) { completed, total ->
                        // Fired from the inference thread as each tile finishes.
                        // The first callback (completed == 0) marks the tile-loop
                        // start; every later callback stamps the moment a tile
                        // landed so the meter above can run a real countdown.
                        val now = SystemClock.elapsedRealtime()
                        if (completed == 0) {
                            tilesStartedAt.set(now)
                            lastTileCompletedAt.set(0L)
                        } else {
                            lastTileCompletedAt.set(now)
                        }
                        // Ignore stray callbacks from a run that was cancelled
                        // or superseded, so they can't rewind the live counter.
                        if (runToken.get() != token) return@upscaleImage
                        meter.update { it.copy(completedTiles = completed, totalTiles = total) }
                    }
                } finally {
                    meterJob.cancel()
                    // Only the live run may reset the meter; a stale run must not
                    // wipe the meter of the run that replaced it.
                    if (runToken.get() == token) meter.value = ProcessingMeter()
                }
                if (runToken.get() == token) when (outcome) {
                    is UpscaleOutcome.Success -> resultPath.value = outcome.resultPath
                    is UpscaleOutcome.Failure -> messageChannel.send(
                        AiUiMessage(
                            when (outcome.reason) {
                                UpscaleOutcome.Reason.NO_MODEL -> R.string.msg_upscale_no_model
                                UpscaleOutcome.Reason.SOURCE_UNREADABLE ->
                                    R.string.msg_upscale_source_unreadable
                                UpscaleOutcome.Reason.FAILED -> R.string.msg_upscale_failed
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
    fun cancelUpscale() {
        if (!processing.value) return
        // Invalidate the run first so any late progress callbacks from the
        // still-draining native loop (and its meter reset) become no-ops.
        runToken.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        processing.value = false
        meter.value = ProcessingMeter()
        viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_upscale_cancelled)) }
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
                    is UpscaleSaveOutcome.Success ->
                        AiUiMessage(R.string.msg_upscale_saved, outcome.displayName)
                    is UpscaleSaveOutcome.Failure ->
                        AiUiMessage(R.string.msg_upscale_save_failed)
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
        val mode: UpscaleMode = UpscaleMode.AUTO,
        val strength: Float = ImageUpscaleUiState.DEFAULT_STRENGTH,
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
    )
}
