package id.andreasmbngaol.agallery.presentation.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.model.trash.TrashItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import id.andreasmbngaol.agallery.domain.usecase.trash.FinalizePermanentDeleteUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.RestoreFromTrashUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * VM for the Trash screen (app-level soft-delete).
 *
 * ## Restore flow
 * Just call [RestoreFromTrashUseCase] -> the Room `trashed` row is removed and the
 * item automatically reappears in the main grid because `observeMedia()` filters
 * based on `observeTrashedIds()`.
 *
 * ## Permanent-delete flow
 * 1. The UI taps "Delete forever" -> [requestPermanentDelete] is called.
 * 2. The VM requests an IntentSender from [MediaRepository.createDeleteRequest] &
 *    emits it via [deleteRequests].
 * 3. The UI launches the IntentSender via ActivityResultContracts. When the user
 *    approves, the system deletes the MediaStore file.
 * 4. The UI calls [confirmPermanentDelete] with the mediaId so the Room row is
 *    cleaned up too (preventing a ghost record).
 *
 * ## 30-day auto-purge (retention)
 * Trash in this app is INTERNAL (metadata in Room; the MediaStore file still
 * exists). Because the app now REQUIRES All-files access, file deletion can be
 * done DIRECTLY without a system dialog. The MAIN auto-purge runs in the
 * background via TrashPurgeWorker (daily). [autoPurgeExpired] is still used as a
 * fallback when the Trash screen opens: it collects items older than
 * [RETENTION_DAYS] days and deletes them directly (createDeleteRequest returns
 * null -> the file is deleted & the Room row cleaned up without a dialog).
 */
class TrashViewModel(
    observeTrashItems: ObserveTrashItemsUseCase,
    getSettings: GetSettingsUseCase,
    private val restoreFromTrash: RestoreFromTrashUseCase,
    private val finalizePermanentDelete: FinalizePermanentDeleteUseCase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val items: StateFlow<List<TrashItem>> = observeTrashItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    val componentStyle: StateFlow<ComponentStyle?> = getSettings()
        .map { it.componentStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val edgeEffectMode: StateFlow<EdgeEffectMode?> = getSettings()
        .map { it.edgeEffectMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests

    fun restore(mediaId: Long) {
        viewModelScope.launch { restoreFromTrash(mediaId) }
    }

    /** Restore many items at once (used by the "Restore all" action). */
    fun restoreMany(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            mediaIds.forEach { restoreFromTrash(it) }
        }
    }

    /**
     * Request a SAF delete dialog for MANY items at once. MediaStore supports a
     * single IntentSender for many URIs, so the user only confirms once. If null
     * (older API), finalize everything directly.
     */
    fun requestPermanentDeleteMany(items: List<TrashItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val sender = mediaRepository.createDeleteRequest(items.map { it.uri })
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                items.forEach { finalizePermanentDelete(it.id) }
            }
        }
    }

    /** Called by the UI after the (bulk) SAF delete dialog is approved. */
    fun confirmPermanentDeleteMany(mediaIds: List<Long>) {
        viewModelScope.launch {
            mediaIds.forEach { finalizePermanentDelete(it) }
        }
    }

    /**
     * Request a SAF delete dialog for [item]. If MediaStore returns a non-null
     * IntentSender (API 30+), the UI must launch it and then call
     * [confirmPermanentDelete] on RESULT_OK. If null (older API or a file we own),
     * the file is already deleted -> call finalize now.
     */
    fun requestPermanentDelete(item: TrashItem) {
        viewModelScope.launch {
            val sender = mediaRepository.createDeleteRequest(listOf(item.uri))
            if (sender != null) {
                _deleteRequests.emit(sender)
            } else {
                finalizePermanentDelete(item.id)
            }
        }
    }

    /** Called by the UI after the SAF delete dialog is approved. */
    fun confirmPermanentDelete(mediaId: Long) {
        viewModelScope.launch { finalizePermanentDelete(mediaId) }
    }

    /**
     * Load detailed metadata (size, dimensions, folder) on-demand for the detail
     * panel in the TrashViewer (swipe up / the Details button). Same as the flow
     * in PhotoViewer so the grid query stays lightweight.
     */
    suspend fun loadDetails(uri: String): MediaDetails? = mediaRepository.getMediaDetails(uri)

    /**
     * Collect items older than the [RETENTION_DAYS]-day retention. Used by the UI
     * for auto-purge when the Trash screen opens.
     */
    fun expiredItems(now: Long = System.currentTimeMillis()): List<TrashItem> {
        val threshold = RETENTION_DAYS * 86_400_000L
        return items.value.filter { now - it.trashedAt >= threshold }
    }

    /**
     * Auto-purge expired items (opportunistic, when the screen opens). Uses the
     * SAF delete-many flow -> one system confirmation dialog for all items past 30
     * days. Returns the list of submitted ids so the UI can finalize the Room rows
     * after the user approves. Returns empty when nothing is expired (the UI does
     * not need to do anything).
     */
    fun autoPurgeExpired(): List<Long> {
        val expired = expiredItems()
        if (expired.isEmpty()) return emptyList()
        requestPermanentDeleteMany(expired)
        return expired.map { it.id }
    }

    companion object {
        const val RETENTION_DAYS = 30L
    }
}
