package id.andreasmbngaol.agallery.presentation.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.TrashItem
import id.andreasmbngaol.agallery.domain.repository.MediaRepository
import id.andreasmbngaol.agallery.domain.usecase.FinalizePermanentDeleteUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.RestoreFromTrashUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * VM untuk layar Trash (app-level soft-delete).
 *
 * ## Alur Restore
 * Cukup panggil [RestoreFromTrashUseCase] -> row Room `trashed` hilang, item
 * otomatis muncul kembali di grid utama karena `observeMedia()` mem-filter
 * berdasarkan `observeTrashedIds()`.
 *
 * ## Alur Delete permanent
 * 1. UI klik "Delete forever" -> [requestPermanentDelete] dipanggil.
 * 2. VM minta IntentSender dari [MediaRepository.createDeleteRequest] &
 *    memancarkan-nya lewat [deleteRequests].
 * 3. UI meluncurkan IntentSender via ActivityResultContracts. Saat user
 *    menyetujui, sistem menghapus file MediaStore.
 * 4. UI memanggil [confirmPermanentDelete] dgn mediaId supaya row Room
 *    juga ikut dibersihkan (mencegah ghost record).
 */
class TrashViewModel(
    observeTrashItems: ObserveTrashItemsUseCase,
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

    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests

    fun restore(mediaId: Long) {
        viewModelScope.launch { restoreFromTrash(mediaId) }
    }

    /** Restore banyak item sekaligus (dipakai action "Restore all"). */
    fun restoreMany(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            mediaIds.forEach { restoreFromTrash(it) }
        }
    }

    /**
     * Request SAF delete-dialog untuk BANYAK item sekaligus. MediaStore
     * mendukung satu IntentSender untuk banyak URI, jadi user cukup 1x
     * konfirmasi. Kalau null (API lama), langsung finalize semua.
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

    /** Dipanggil UI setelah SAF delete-dialog (bulk) di-approve. */
    fun confirmPermanentDeleteMany(mediaIds: List<Long>) {
        viewModelScope.launch {
            mediaIds.forEach { finalizePermanentDelete(it) }
        }
    }

    /**
     * Request SAF delete-dialog untuk [item]. Kalau MediaStore mengembalikan
     * IntentSender != null (API 30+), UI wajib meluncurkan-nya lalu memanggil
     * [confirmPermanentDelete] pada RESULT_OK. Kalau null (API lama atau file
     * kita miliki), file sudah langsung terhapus -> panggil finalize sekarang.
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

    /** Dipanggil UI setelah SAF delete-dialog di-approve. */
    fun confirmPermanentDelete(mediaId: Long) {
        viewModelScope.launch { finalizePermanentDelete(mediaId) }
    }
}
