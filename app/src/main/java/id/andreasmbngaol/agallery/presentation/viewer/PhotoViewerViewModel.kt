package id.andreasmbngaol.agallery.presentation.viewer

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import id.andreasmbngaol.agallery.domain.model.ai.AiFeature
import id.andreasmbngaol.agallery.domain.model.ai.BackgroundRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.ai.RemovalQuality
import id.andreasmbngaol.agallery.domain.usecase.ai.ObserveModelStatusUseCase
import id.andreasmbngaol.agallery.domain.usecase.ai.RemoveBackgroundUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.qr.QrDetector
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.conversion.ConversionOutcome
import id.andreasmbngaol.agallery.domain.model.conversion.ImageFormat
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.settings.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataCategory
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataRemovalOutcome
import id.andreasmbngaol.agallery.domain.model.qr.QrContent
import id.andreasmbngaol.agallery.domain.usecase.editing.ConvertImageFormatUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.CopyMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.DeleteMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetAllMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.GetMediaDetailsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.MoveMediaToAlbumUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.MoveToTrashUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ObserveFavoriteIdsUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RemoveMetadataUseCase
import id.andreasmbngaol.agallery.domain.usecase.editing.RenameMediaUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.SetAlbumCoverUseCase
import id.andreasmbngaol.agallery.domain.usecase.favorite.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for [PhotoViewerScreen]. The scope is now a [MediaScope] so the
 * viewer can be opened from any smart album (Recent, Videos, Screenshots,
 * Favorites) besides a regular folder.
 */
class PhotoViewerViewModel(
    private val appContext: Context,
    getAllMedia: GetAllMediaUseCase,
    observeFavoriteIds: ObserveFavoriteIdsUseCase,
    private val getSettings: GetSettingsUseCase,
    private val getAlbums: GetAlbumsUseCase,
    private val getMediaDetails: GetMediaDetailsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val deleteMedia: DeleteMediaUseCase,
    private val renameMediaUseCase: RenameMediaUseCase,
    private val moveToAlbumUseCase: MoveMediaToAlbumUseCase,
    private val copyToAlbumUseCase: CopyMediaToAlbumUseCase,
    private val removeMetadataUseCase: RemoveMetadataUseCase,
    private val convertImageFormatUseCase: ConvertImageFormatUseCase,
    private val setAlbumCoverUseCase: SetAlbumCoverUseCase,
    private val qrDetector: QrDetector,
    private val removeBackgroundUseCase: RemoveBackgroundUseCase,
    private val observeModelStatus: ObserveModelStatusUseCase,
) : ViewModel() {
    private data class ViewerParams(
        val sortOrder: GallerySortOrder,
        val scope: MediaScope,
    )

    private val _params = MutableStateFlow<ViewerParams?>(null)
    private val _refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<List<MediaItem>?> =
        combine(_params.filterNotNull(), _refresh) { params, _ -> params }
            .flatMapLatest { params ->
                flow { emit(getAllMedia(params.sortOrder, params.scope)) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = null,
            )

    val favoriteIds: StateFlow<Set<Long>> = observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptySet(),
        )

    val edgeEffectMode: StateFlow<EdgeEffectMode?> = getSettings()
        .map { it.edgeEffectMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    val componentStyle: StateFlow<ComponentStyle?> = getSettings()
        .map { it.componentStyle }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()

    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pendingWriteAction: (suspend () -> Unit)? = null

    private val _qrDetections = MutableStateFlow<Map<Long, List<QrContent>>>(emptyMap())
    val qrDetections: StateFlow<Map<Long, List<QrContent>>> = _qrDetections.asStateFlow()
    private val qrInFlight = mutableSetOf<Long>()

    /**
     * Scan the QR in [item] (photos only), once per media id, then cache the
     * result. Called from the viewer after the page stays idle for a moment
     * (debounced in the UI). Safe to call repeatedly — deduped via cache + inFlight.
     */
    fun detectQr(item: MediaItem) {
        if (item.type != MediaType.IMAGE) return
        if (_qrDetections.value.containsKey(item.id) || item.id in qrInFlight) return
        qrInFlight += item.id
        viewModelScope.launch {
            val result = qrDetector.detect(item.uri)
            _qrDetections.update { it + (item.id to result) }
            qrInFlight -= item.id
        }
    }

    private val _liftState = MutableStateFlow<SubjectLiftState>(SubjectLiftState.Idle)

    /** State of the iOS-style "lift subject" overlay (long-press in the viewer). */
    val liftState: StateFlow<SubjectLiftState> = _liftState.asStateFlow()

    private var liftJob: Job? = null

    /**
     * Long-press "lift subject": run background removal using the user's chosen
     * lift model + quality (read fresh from settings on each gesture), falling
     * back to the smallest installed model at Eco quality when set to Auto. Then
     * expose the transparent cutout so the viewer can show it draggable with
     * Copy / Share. No-op for videos or while a lift is already in flight.
     */
    fun liftSubject(item: MediaItem) {
        if (item.type != MediaType.IMAGE) return
        if (_liftState.value !is SubjectLiftState.Idle) return
        liftJob?.cancel()
        liftJob = viewModelScope.launch {
            _liftState.value = SubjectLiftState.Processing
            val settings = getSettings().first()
            val installed = observeModelStatus(AiFeature.BACKGROUND_REMOVAL).first()
                .filter { it.isInstalled }
            val preferred = settings.liftModelId?.let { pref ->
                installed.firstOrNull { it.spec.id.value == pref }
            }
            val chosen = preferred ?: installed.minByOrNull { it.spec.approxSizeBytes }
            if (chosen == null) {
                _liftState.value = SubjectLiftState.Idle
                _messages.emit(appContext.getString(R.string.msg_lift_no_model))
                return@launch
            }
            val quality = if (chosen.spec.offersQualityChoice) settings.liftQuality else RemovalQuality.ECO
            val outcome = removeBackgroundUseCase(item.uri, chosen.spec.id, quality)
            if (_liftState.value !is SubjectLiftState.Processing) return@launch
            when (outcome) {
                is BackgroundRemovalOutcome.Success -> {
                    val cutout = decodeCutout(outcome.resultPath)
                    if (cutout != null && _liftState.value is SubjectLiftState.Processing) {
                        _liftState.value = SubjectLiftState.Lifted(
                            cutout = cutout.bitmap,
                            resultPath = outcome.resultPath,
                            sourceDisplayName = item.displayName,
                            subjectTopFraction = cutout.topFraction,
                            subjectCenterXFraction = cutout.centerXFraction,
                        )
                    } else {
                        _liftState.value = SubjectLiftState.Idle
                        if (cutout == null) {
                            _messages.emit(appContext.getString(R.string.msg_lift_failed))
                        }
                    }
                }
                is BackgroundRemovalOutcome.Failure -> {
                    _liftState.value = SubjectLiftState.Idle
                    _messages.emit(appContext.getString(R.string.msg_lift_failed))
                }
            }
        }
    }

    /** Dismiss the lift overlay and cancel any in-flight inference. */
    fun dismissLift() {
        liftJob?.cancel()
        liftJob = null
        _liftState.value = SubjectLiftState.Idle
    }

    private data class Cutout(
        val bitmap: ImageBitmap,
        val topFraction: Float,
        val centerXFraction: Float,
    )

    private suspend fun decodeCutout(path: String): Cutout? = withContext(Dispatchers.IO) {
        try {
            val bmp = BitmapFactory.decodeFile(path) ?: return@withContext null
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) return@withContext null
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val step = maxOf(1, minOf(w, h) / 256)
            var minY = h
            var sumX = 0.0
            var count = 0L
            var y = 0
            while (y < h) {
                val row = y * w
                var x = 0
                while (x < w) {
                    val alpha = pixels[row + x] ushr 24
                    if (alpha > 24) {
                        if (y < minY) minY = y
                        sumX += x
                        count++
                    }
                    x += step
                }
                y += step
            }
            val topFraction = if (count == 0L) 0f else minY.toFloat() / h
            val centerXFraction = if (count == 0L) 0.5f else (sumX / count).toFloat() / w
            Cutout(bmp.asImageBitmap(), topFraction, centerXFraction)
        } catch (_: Throwable) {
            null
        }
    }

    fun setParams(sortOrder: GallerySortOrder, scope: MediaScope) {
        val next = ViewerParams(sortOrder, scope)
        if (_params.value != next) _params.value = next
    }

    fun refresh() {
        _refresh.value += 1
    }

    suspend fun loadDetails(uri: String): MediaDetails? = getMediaDetails(uri)

    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = getAlbums().filter { !it.isSmart }
        }
    }

    fun onToggleFavorite(mediaId: Long, isFavorite: Boolean) {
        viewModelScope.launch { toggleFavorite(mediaId, isFavorite) }
    }

    /**
     * Make [mediaId] the cover of album [albumKey] ("Set as Cover").
     * Reactive: the album list automatically uses the new cover via observeAlbums().
     */
    fun setAlbumCover(albumKey: String, mediaId: Long) {
        viewModelScope.launch {
            setAlbumCoverUseCase(albumKey, mediaId)
            _messages.emit(appContext.getString(R.string.msg_album_cover_updated))
        }
    }

    fun moveToTrash(item: MediaItem) {
        viewModelScope.launch {
            moveToTrashUseCase(item)
            refresh()
            _messages.emit(appContext.getString(R.string.msg_moved_to_trash))
        }
    }

    fun deletePhoto(uri: String) {
        viewModelScope.launch {
            val sender = deleteMedia(listOf(uri))
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                refresh()
                _messages.emit(appContext.getString(R.string.msg_deleted))
            }
        }
    }

    fun rename(uri: String, newName: String) {
        viewModelScope.launch {
            val sender = renameMediaUseCase(uri, newName)
            if (sender != null) {
                pendingWriteAction = {
                    renameMediaUseCase(uri, newName)
                    refresh()
                    _messages.emit(appContext.getString(R.string.msg_renamed))
                }
                _writeRequests.emit(sender)
            } else {
                refresh()
                _messages.emit(appContext.getString(R.string.msg_renamed))
            }
        }
    }

    fun moveToAlbum(uri: String, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            val sender = moveToAlbumUseCase(uri, path)
            if (sender != null) {
                pendingWriteAction = {
                    moveToAlbumUseCase(uri, path)
                    refresh()
                    _messages.emit(appContext.getString(R.string.msg_moved_to_album, albumName))
                }
                _writeRequests.emit(sender)
            } else {
                refresh()
                _messages.emit(appContext.getString(R.string.msg_moved_to_album, albumName))
            }
        }
    }

    fun copyToAlbum(item: MediaItem, albumName: String) {
        val path = albumRelativePath(albumName)
        viewModelScope.launch {
            copyToAlbumUseCase(item, path)
            _messages.emit(appContext.getString(R.string.msg_copied_to_album, albumName))
        }
    }

    /**
     * Remove the selected metadata from [item]. If the file is not owned by the
     * app, the existing write-consent path is reused (pendingWriteAction) and the
     * operation is retried automatically after the user approves.
     */
    fun removeMetadata(
        item: MediaItem,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ) {
        viewModelScope.launch { runRemoveMetadata(item, categories, saveAsCopy) }
    }

    private suspend fun runRemoveMetadata(
        item: MediaItem,
        categories: Set<MetadataCategory>,
        saveAsCopy: Boolean,
    ) {
        when (val outcome = removeMetadataUseCase(item.uri, categories, saveAsCopy)) {
            is MetadataRemovalOutcome.Success -> {
                refresh()
                val msg = if (outcome.savedAsCopy) {
                    R.string.msg_metadata_copy_created
                } else {
                    R.string.msg_metadata_removed
                }
                _messages.emit(appContext.getString(msg))
            }
            is MetadataRemovalOutcome.NeedsConsent -> {
                pendingWriteAction = { runRemoveMetadata(item, categories, saveAsCopy) }
                _writeRequests.emit(outcome.intentSender)
            }
            MetadataRemovalOutcome.UnsupportedFormat ->
                _messages.emit(appContext.getString(R.string.msg_metadata_unsupported))
            MetadataRemovalOutcome.Failed ->
                _messages.emit(appContext.getString(R.string.msg_metadata_failed))
        }
    }

    /**
     * Convert [item] to the [target] format ([quality] 1..100 for lossy). Conversion
     * always creates a new file; if [deleteOriginal] is true, the original is moved
     * to Trash (Room-based soft-trash, without a MediaStore consent dialog).
     */
    fun convertFormat(
        item: MediaItem,
        target: ImageFormat,
        quality: Int,
        deleteOriginal: Boolean,
    ) {
        viewModelScope.launch {
            when (val outcome = convertImageFormatUseCase(item.uri, target, quality)) {
                is ConversionOutcome.Success -> {
                    if (deleteOriginal) {
                        moveToTrashUseCase(item)
                    }
                    refresh()
                    val msg = if (deleteOriginal) {
                        appContext.getString(
                            R.string.msg_converted_replaced,
                            outcome.displayName,
                        )
                    } else {
                        appContext.getString(
                            R.string.msg_converted_saved,
                            outcome.displayName,
                        )
                    }
                    _messages.emit(msg)
                }
                ConversionOutcome.UnsupportedSource ->
                    _messages.emit(
                        appContext.getString(R.string.msg_convert_unsupported_source),
                    )
                ConversionOutcome.UnsupportedTarget ->
                    _messages.emit(
                        appContext.getString(R.string.msg_convert_unsupported_target),
                    )
                ConversionOutcome.Failed ->
                    _messages.emit(appContext.getString(R.string.msg_convert_failed))
            }
        }
    }

    fun onWriteConsentGranted() {
        val action = pendingWriteAction ?: return
        pendingWriteAction = null
        viewModelScope.launch { action() }
    }

    fun onWriteConsentDenied() {
        pendingWriteAction = null
    }

    private fun albumRelativePath(name: String): String {
        val clean = name.trim().trim('/')
        return "DCIM/$clean/"
    }
}


/**
 * State of the viewer's "lift subject" overlay.
 */
sealed interface SubjectLiftState {
    /** No lift in progress. */
    data object Idle : SubjectLiftState

    /** Background removal is running. */
    data object Processing : SubjectLiftState

    /** The cutout is ready and shown draggable with Copy / Share. */
    data class Lifted(
        val cutout: ImageBitmap,
        val resultPath: String,
        val sourceDisplayName: String,
        val subjectTopFraction: Float,
        val subjectCenterXFraction: Float,
    ) : SubjectLiftState
}
