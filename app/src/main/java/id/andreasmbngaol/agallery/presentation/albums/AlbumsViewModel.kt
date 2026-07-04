package id.andreasmbngaol.agallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.ALBUM_KEY_TRASH
import id.andreasmbngaol.agallery.domain.model.Album
import id.andreasmbngaol.agallery.domain.model.DEFAULT_PINNED_ALBUM_KEYS
import id.andreasmbngaol.agallery.domain.model.LOCKED_PIN_ALBUM_KEYS
import id.andreasmbngaol.agallery.domain.model.MediaScope
import id.andreasmbngaol.agallery.domain.usecase.GetAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetPinnedAlbumsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * VM tab Albums. Memuat daftar album (folder + cerdas) dari MediaStore lewat
 * [GetAlbumsUseCase], lalu memecahnya jadi Pinned + More berdasarkan daftar
 * kunci yang tersimpan di [GetSettingsUseCase] (DataStore).
 *
 * Semua operasi pin/unpin/reorder cukup menulis ulang daftar kunci; UI
 * ter-update reaktif via [state].
 */
class AlbumsViewModel(
    private val getAlbums: GetAlbumsUseCase,
    getSettings: GetSettingsUseCase,
    private val setPinnedAlbums: SetPinnedAlbumsUseCase,
    observeTrashItems: ObserveTrashItemsUseCase,
) : ViewModel() {

    // Sumber album mentah. null = belum di-load (state Loading).
    private val _albums = MutableStateFlow<List<Album>?>(null)

    // Urutan pinned dari settings (null di storage -> pakai default).
    private val pinnedKeys: StateFlow<List<String>> = getSettings()
        .map { it.pinnedAlbumKeys ?: DEFAULT_PINNED_ALBUM_KEYS }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DEFAULT_PINNED_ALBUM_KEYS,
        )

    // Jumlah item di Trash. 0 -> row Trash disembunyikan.
    private val trashCount: StateFlow<Int> = observeTrashItems()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0,
        )

    val state: StateFlow<AlbumsUiState> =
        combine(_albums, pinnedKeys, trashCount) { albums, pins, tc ->
            when {
                albums == null -> AlbumsUiState.Loading
                albums.isEmpty() && tc == 0 -> AlbumsUiState.Empty
                else -> buildContent(albums ?: emptyList(), pins, tc)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AlbumsUiState.Loading,
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _albums.value = getAlbums()
        }
    }

    fun pin(key: String) {
        // Trash tidak boleh di-pin -- baris Trash punya lokasi tetap.
        if (key == ALBUM_KEY_TRASH) return
        val current = pinnedKeys.value
        if (key in current) return
        viewModelScope.launch { setPinnedAlbums(current + key) }
    }

    fun unpin(key: String) {
        // Recent / Videos / Favorites terkunci di section pinned.
        if (key in LOCKED_PIN_ALBUM_KEYS) return
        val current = pinnedKeys.value
        if (key !in current) return
        viewModelScope.launch { setPinnedAlbums(current - key) }
    }

    fun setOrder(keys: List<String>) {
        // Buang Trash (kalau tak sengaja ikut) sebelum persist supaya
        // tidak muncul di section Pinned.
        val sanitized = keys.filter { it != ALBUM_KEY_TRASH }
        viewModelScope.launch { setPinnedAlbums(sanitized) }
    }

    private fun buildContent(
        albums: List<Album>,
        pins: List<String>,
        trashCount: Int,
    ): AlbumsUiState.Content {
        val byKey = albums.associateBy { it.key }
        // Locked album keys HARUS selalu ikut di section Pinned. Kalau DataStore
        // user (dari sebelum fitur ini ada) tidak menyimpan salah satu dari
        // Recent / Videos / Favorites, kita sisipkan ke pinned di posisi
        // stabil (append di akhir, di atas custom folder yg mungkin di-pin
        // user) sehingga selalu terlihat & konsisten dgn perilaku locked.
        val ensuredPins = buildList {
            // Hormati urutan user untuk key yg sudah ada.
            pins.forEach { if (it != ALBUM_KEY_TRASH) add(it) }
            // Tambahkan locked yg belum ada (Recent -> Videos -> Favorites).
            LOCKED_PIN_ALBUM_KEYS.forEach { locked -> if (locked !in this) add(locked) }
        }
        val pinned = ensuredPins.mapNotNull { byKey[it] }
        val pinnedSet = pinned.map { it.key }.toHashSet()
        val more = albums
            .filter { it.key !in pinnedSet }
            .sortedBy { it.name.lowercase() }
        // Trash SELALU dirender di paling bawah tab Albums (bahkan saat kosong)
        // supaya user selalu bisa masuk ke layar Trash. Layar Trash sendiri
        // yang menampilkan empty state kalau tidak ada item.
        val trash = Album(
            key = ALBUM_KEY_TRASH,
            scope = MediaScope.Trash,
            name = "Trash",
            coverUri = null,
            photoCount = trashCount,
            videoCount = 0,
            isSmart = true,
        )
        return AlbumsUiState.Content(pinned = pinned, more = more, trash = trash)
    }
}
