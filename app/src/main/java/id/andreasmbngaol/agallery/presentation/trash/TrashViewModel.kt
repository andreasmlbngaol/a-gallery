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
 *
 * ## Auto-purge 30 hari (retensi)
 * Trash di app ini INTERNAL (metadata di Room; file MediaStore masih ada).
 * Karena app kini WAJIB punya All-files access, penghapusan file bisa dilakukan
 * LANGSUNG tanpa dialog sistem. Auto-purge UTAMA dijalankan di background oleh
 * TrashPurgeWorker (harian). [autoPurgeExpired] tetap dipakai sebagai cadangan
 * saat layar Trash dibuka: mengumpulkan item yg umurnya >= [RETENTION_DAYS] hari
 * lalu menghapusnya langsung (createDeleteRequest mengembalikan null -> file
 * dihapus & row Room dibersihkan tanpa perlu dialog).
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

    // Gaya komponen (Solid/Frosted/Glass) & efek tepi dari Settings supaya
    // topbar, island, dan scrim layar Trash SERAGAM dgn layar lain.
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

    /**
     * Muat metadata detail (ukuran, dimensi, folder) on-demand untuk panel
     * detail di TrashViewer (swipe ke atas / tombol Details). Sama seperti alur
     * di PhotoViewer supaya query grid tetap ringan.
     */
    suspend fun loadDetails(uri: String): MediaDetails? = mediaRepository.getMediaDetails(uri)

    /**
     * Kumpulkan item yg umurnya sudah melewati retensi [RETENTION_DAYS] hari.
     * Dipakai UI untuk auto-purge saat layar Trash dibuka.
     */
    fun expiredItems(now: Long = System.currentTimeMillis()): List<TrashItem> {
        val threshold = RETENTION_DAYS * 86_400_000L
        return items.value.filter { now - it.trashedAt >= threshold }
    }

    /**
     * Auto-purge item kedaluwarsa (opportunistic, saat layar dibuka). Memakai
     * alur SAF delete-many -> satu dialog konfirmasi sistem utk semua item yg
     * sudah lewat 30 hari. Mengembalikan daftar id yg diajukan supaya UI bisa
     * finalize row Room setelah user menyetujui. Return kosong bila tak ada
     * yg kedaluwarsa (UI tak perlu melakukan apa-apa).
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
