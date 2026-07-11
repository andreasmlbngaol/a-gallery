package id.andreasmbngaol.agallery.presentation.ai

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ai.DeviceBenchmark
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.AiModelSpec
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOptions
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceOutcome
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceProgress
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceSaveOutcome
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStage
import id.andreasmbngaol.agallery.domain.model.ai.AutoEnhanceStageResult
import id.andreasmbngaol.agallery.domain.model.ai.ModelStatus
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitability
import id.andreasmbngaol.agallery.domain.model.ai.ModelSuitabilityEvaluator
import id.andreasmbngaol.agallery.domain.model.settings.AppSettings
import id.andreasmbngaol.agallery.domain.usecase.ai.AutoEnhanceUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.SaveAutoEnhanceResultUseCase
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
 * Drives the Auto Enhance screen for a single source image: the one-tap pipeline
 * that chains Face Restore -> Enhance -> Upscale via [AutoEnhanceUseCase], then
 * saves the final preview into the gallery as a NEW file on request. The
 * original is never modified and no network access occurs.
 *
 * It observes the installed models of all THREE features so the screen can gate
 * on every required model being present. Like the single-feature view models it
 * measures progress in TILES and runs a live meter with an ETA countdown, but it
 * additionally tracks WHICH stage is running and keeps every finished stage's
 * preview so the result is shown step by step. A [runToken] gates late progress
 * callbacks from the uninterruptible native tile loop so a cancelled/superseded
 * run can't corrupt the live one.
 */
class AutoEnhanceViewModel(
    private val sourceUri: String,
    private val sourceDisplayName: String,
    observeModelStatus: ObserveModelStatusUseCase,
    getSettings: GetSettingsUseCase,
    private val autoEnhance: AutoEnhanceUseCase,
    private val saveResult: SaveAutoEnhanceResultUseCase,
    private val deviceBenchmark: DeviceBenchmark,
) : ViewModel() {

    private val options = MutableStateFlow(AutoEnhanceOptions())
    private val work = MutableStateFlow(WorkState())

    private var inferenceJob: Job? = null
    private val runToken = AtomicLong(0L)

    private val messageChannel = Channel<AiUiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val faceStatuses = observeModelStatus(AiFeature.FACE_RESTORATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
    private val enhanceStatuses = observeModelStatus(AiFeature.IMAGE_ENHANCE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
    private val upscaleStatuses = observeModelStatus(AiFeature.IMAGE_UPSCALE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    // Null until every feature's status has loaded, so the screen can show a
    // single "checking" state instead of flickering per-feature.
    private val models: StateFlow<ModelsState?> =
        combine(faceStatuses, enhanceStatuses, upscaleStatuses) { face, enhance, upscale ->
            if (face == null || enhance == null || upscale == null) {
                null
            } else {
                ModelsState(
                    faceModels = installedSpecs(face),
                    enhanceModels = installedSpecs(enhance),
                    upscaleModels = installedSpecs(upscale),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    val uiState: StateFlow<AutoEnhanceUiState> = combine(
        models,
        options,
        work,
        settings,
    ) { m, o, w, s ->
        AutoEnhanceUiState(
            sourceUri = sourceUri,
            sourceDisplayName = sourceDisplayName,
            checkingModels = m == null,
            hasFaceModel = m?.hasFace == true,
            hasEnhanceModel = m?.hasEnhance == true,
            hasUpscaleModel = m?.hasUpscale == true,
            hasAllModels = m?.hasAllModels == true,
            runFaceRestore = o.runFaceRestore,
            runEnhance = o.runEnhance,
            runUpscale = o.runUpscale,
            faceStrength = o.faceStrength,
            enhanceStrength = o.enhanceStrength,
            upscaleStrength = o.upscaleStrength,
            processing = w.processing,
            saving = w.saving,
            currentStage = w.currentStage,
            plannedStages = w.plannedStages,
            stageResults = w.stageResults,
            faceSkipped = w.faceSkipped,
            finalPath = w.finalPath,
            componentStyleChosen = s.componentStyle,
            edgeEffectMode = s.edgeEffectMode,
            processingCompletedTiles = w.completedTiles,
            processingTotalTiles = w.totalTiles,
            processingElapsedSeconds = w.elapsedSeconds,
            processingUsedMemoryBytes = w.usedMemoryBytes,
            processingEtaSeconds = w.etaSeconds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        AutoEnhanceUiState(sourceUri = sourceUri, sourceDisplayName = sourceDisplayName),
    )

    /** Toggle the Face Restore stage; clears any stale preview. */
    fun setFaceRestore(enabled: Boolean) {
        options.update { it.copy(runFaceRestore = enabled) }
        clearResult()
    }

    /** Toggle the Enhance stage; clears any stale preview. */
    fun setEnhance(enabled: Boolean) {
        options.update { it.copy(runEnhance = enabled) }
        clearResult()
    }

    /** Toggle the Upscale stage; clears any stale preview. */
    fun setUpscale(enabled: Boolean) {
        options.update { it.copy(runUpscale = enabled) }
        clearResult()
    }

    /** Set the Face Restore blend strength (0..1); clears any stale preview. */
    fun setFaceStrength(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (options.value.faceStrength == clamped) return
        options.update { it.copy(faceStrength = clamped) }
        clearResult()
    }

    /** Set the Enhance blend strength (0..1); clears any stale preview. */
    fun setEnhanceStrength(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (options.value.enhanceStrength == clamped) return
        options.update { it.copy(enhanceStrength = clamped) }
        clearResult()
    }

    /** Set the Upscale blend strength (0..1); clears any stale preview. */
    fun setUpscaleStrength(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (options.value.upscaleStrength == clamped) return
        options.update { it.copy(upscaleStrength = clamped) }
        clearResult()
    }

    /** Run the pipeline with the effective models. No-op while busy. */
    fun run() {
        if (work.value.processing) return
        val m = models.value ?: return
        val opts = options.value
        if (!opts.hasAnyStage) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_auto_enhance_nothing)) }
            return
        }
        if (!m.hasAllModels) {
            viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_auto_enhance_no_model)) }
            return
        }
        val planned = buildList {
            // Execution order: Enhance -> Upscale -> Face Restore (face last so a
            // restored face is never re-upscaled). Keep in sync with AutoEnhanceUseCase.
            if (opts.runEnhance) add(AutoEnhanceStage.ENHANCE)
            if (opts.runUpscale) add(AutoEnhanceStage.UPSCALE)
            if (opts.runFaceRestore) add(AutoEnhanceStage.FACE_RESTORE)
        }
        work.update {
            it.copy(
                processing = true,
                finalPath = null,
                stageResults = emptyList(),
                faceSkipped = false,
                plannedStages = planned,
                currentStage = null,
                completedTiles = 0,
                totalTiles = 0,
                etaSeconds = -1,
            )
        }
        val token = runToken.incrementAndGet()
        inferenceJob = viewModelScope.launch {
            try {
                // Fail fast if the device almost certainly lacks the RAM for the
                // heaviest enabled stage: heavy models are killed by the OS
                // Low-Memory-Killer (uncatchable), so we warn instead of letting
                // the app force-close mid-run.
                val capability = withContext(Dispatchers.Default) { deviceBenchmark.measure() }
                val tooHeavy = plannedSpecs(opts, m).firstOrNull { spec ->
                    spec.estimatedPeakMemoryBytes > 0L &&
                        ModelSuitabilityEvaluator.evaluate(spec, capability).rating ==
                        ModelSuitability.Rating.INSUFFICIENT_MEMORY
                }
                if (tooHeavy != null) {
                    messageChannel.send(
                        AiUiMessage(R.string.msg_auto_enhance_insufficient_memory, tooHeavy.displayName),
                    )
                    return@launch
                }

                // Wall-clock at which the current stage's tile loop started and at
                // which its most recent tile landed; together they let the ETA
                // count DOWN smoothly instead of only stepping per tile. Both are
                // reset at every stage boundary.
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
                        if (runToken.get() != token) break
                        work.update { w ->
                            val eta = if (w.completedTiles > 0 && w.totalTiles > 0 && start > 0L && lastAt > start) {
                                val avgPerTileMs = (lastAt - start).toDouble() / w.completedTiles
                                val remaining = (w.totalTiles - w.completedTiles).coerceAtLeast(0)
                                val sinceLastMs = (now - lastAt).toDouble()
                                val remainingMs = (avgPerTileMs * remaining - sinceLastMs).coerceAtLeast(0.0)
                                ceil(remainingMs / 1000.0).toInt()
                            } else {
                                -1
                            }
                            w.copy(
                                elapsedSeconds = elapsed,
                                usedMemoryBytes = info.totalPss.toLong() * 1024L,
                                etaSeconds = eta,
                            )
                        }
                        delay(500L.milliseconds)
                    }
                }

                val outcome = try {
                    autoEnhance(sourceUri, opts) { progress ->
                        // Ignore stray callbacks from a run that was cancelled or
                        // superseded, so they can't rewind the live counter.
                        if (runToken.get() != token) return@autoEnhance
                        when (progress) {
                            is AutoEnhanceProgress.Started -> {
                                tilesStartedAt.set(0L)
                                lastTileCompletedAt.set(0L)
                                work.update {
                                    it.copy(
                                        currentStage = progress.stage,
                                        completedTiles = 0,
                                        totalTiles = 0,
                                        etaSeconds = -1,
                                    )
                                }
                            }

                            is AutoEnhanceProgress.Tiles -> {
                                val now = SystemClock.elapsedRealtime()
                                if (progress.completed == 0) {
                                    tilesStartedAt.set(now)
                                    lastTileCompletedAt.set(0L)
                                } else {
                                    lastTileCompletedAt.set(now)
                                }
                                work.update {
                                    it.copy(
                                        completedTiles = progress.completed,
                                        totalTiles = progress.total,
                                    )
                                }
                            }

                            is AutoEnhanceProgress.Completed -> work.update {
                                it.copy(
                                    stageResults = it.stageResults +
                                        AutoEnhanceStageResult(progress.stage, progress.resultPath),
                                    currentStage = null,
                                    completedTiles = 0,
                                    totalTiles = 0,
                                    etaSeconds = -1,
                                )
                            }

                            is AutoEnhanceProgress.Skipped -> work.update {
                                it.copy(faceSkipped = true)
                            }
                        }
                    }
                } finally {
                    meterJob.cancel()
                }
                if (runToken.get() == token) when (outcome) {
                    is AutoEnhanceOutcome.Success -> work.update { it.copy(finalPath = outcome.finalPath) }
                    is AutoEnhanceOutcome.Failure -> messageChannel.send(
                        AiUiMessage(
                            when (outcome.reason) {
                                AutoEnhanceOutcome.Reason.NO_MODEL -> R.string.msg_auto_enhance_no_model
                                AutoEnhanceOutcome.Reason.SOURCE_UNREADABLE ->
                                    R.string.msg_auto_enhance_source_unreadable
                                AutoEnhanceOutcome.Reason.FAILED -> R.string.msg_auto_enhance_failed
                                AutoEnhanceOutcome.Reason.NOTHING_TO_DO -> R.string.msg_auto_enhance_nothing
                                AutoEnhanceOutcome.Reason.NOTHING_PRODUCED ->
                                    R.string.msg_auto_enhance_nothing_produced
                            },
                        ),
                    )
                }
            } finally {
                if (runToken.get() == token) {
                    work.update {
                        it.copy(
                            processing = false,
                            currentStage = null,
                            completedTiles = 0,
                            totalTiles = 0,
                            etaSeconds = -1,
                        )
                    }
                }
            }
        }
    }

    /**
     * Cancel an in-progress run. The dialog dismisses immediately; the native
     * inference call cannot be interrupted mid-tile, so it finishes in the
     * background and its (now discarded) result is ignored.
     */
    fun cancel() {
        if (!work.value.processing) return
        runToken.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        work.update {
            it.copy(
                processing = false,
                currentStage = null,
                completedTiles = 0,
                totalTiles = 0,
                etaSeconds = -1,
            )
        }
        viewModelScope.launch { messageChannel.send(AiUiMessage(R.string.msg_auto_enhance_cancelled)) }
    }

    /** Save the final preview into the gallery as a new file. */
    fun save() {
        val path = work.value.finalPath ?: return
        if (work.value.saving) return
        work.update { it.copy(saving = true) }
        viewModelScope.launch {
            val outcome = saveResult(path, sourceDisplayName)
            work.update { it.copy(saving = false) }
            messageChannel.send(
                when (outcome) {
                    is AutoEnhanceSaveOutcome.Success ->
                        AiUiMessage(R.string.msg_auto_enhance_saved, outcome.displayName)
                    is AutoEnhanceSaveOutcome.Failure ->
                        AiUiMessage(R.string.msg_auto_enhance_save_failed)
                },
            )
        }
    }

    private fun clearResult() {
        if (work.value.processing) return
        work.update { it.copy(finalPath = null, stageResults = emptyList(), faceSkipped = false) }
    }

    private fun installedSpecs(statuses: List<ModelStatus>): List<AiModelSpec> =
        statuses.filter { it.isInstalled }.map { it.spec }

    /** The effective model spec chosen for each enabled stage, for the RAM pre-check. */
    private fun plannedSpecs(options: AutoEnhanceOptions, models: ModelsState): List<AiModelSpec> =
        buildList {
            if (options.runEnhance) pick(models.enhanceModels, ENHANCE_PREFERENCE)?.let(::add)
            if (options.runUpscale) pick(models.upscaleModels, UPSCALE_PREFERENCE)?.let(::add)
            if (options.runFaceRestore) pick(models.faceModels, FACE_PREFERENCE)?.let(::add)
        }

    private fun pick(models: List<AiModelSpec>, preference: List<String>): AiModelSpec? =
        preference.firstNotNullOfOrNull { pref -> models.firstOrNull { it.id.value == pref } }
            ?: models.firstOrNull()

    private data class ModelsState(
        val faceModels: List<AiModelSpec>,
        val enhanceModels: List<AiModelSpec>,
        val upscaleModels: List<AiModelSpec>,
    ) {
        val hasFace: Boolean get() = faceModels.isNotEmpty()
        val hasEnhance: Boolean get() = enhanceModels.isNotEmpty()
        val hasUpscale: Boolean get() = upscaleModels.isNotEmpty()
        val hasAllModels: Boolean get() = hasFace && hasEnhance && hasUpscale
    }

    private data class WorkState(
        val processing: Boolean = false,
        val saving: Boolean = false,
        val finalPath: String? = null,
        val stageResults: List<AutoEnhanceStageResult> = emptyList(),
        val currentStage: AutoEnhanceStage? = null,
        val plannedStages: List<AutoEnhanceStage> = emptyList(),
        val faceSkipped: Boolean = false,
        val completedTiles: Int = 0,
        val totalTiles: Int = 0,
        val elapsedSeconds: Int = 0,
        val usedMemoryBytes: Long = 0L,
        val etaSeconds: Int = -1,
    )

    private companion object {
        val FACE_PREFERENCE = listOf("gpen-bfr-512", "gpen-bfr-256")
        val ENHANCE_PREFERENCE = listOf("scunet-gan", "scunet-psnr")
        val UPSCALE_PREFERENCE = listOf("real-esrgan-general-x4v3", "real-esrgan-x4plus")
    }
}
