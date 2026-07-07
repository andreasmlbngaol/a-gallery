package id.andreasmbngaol.agallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.album.ALBUM_KEY_TRASH
import id.andreasmbngaol.agallery.domain.model.album.Album
import id.andreasmbngaol.agallery.domain.model.album.DEFAULT_PINNED_ALBUM_KEYS
import id.andreasmbngaol.agallery.domain.model.album.LOCKED_PIN_ALBUM_KEYS
import id.andreasmbngaol.agallery.domain.model.media.MediaScope
import id.andreasmbngaol.agallery.domain.usecase.settings.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.media.ObserveAlbumsUseCase
import id.andreasmbngaol.agallery.domain.usecase.trash.ObserveTrashItemsUseCase
import id.andreasmbngaol.agallery.domain.usecase.settings.SetPinnedAlbumsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Albums tab VM. Loads the album list (folders + smart albums) REACTIVELY via
 * [ObserveAlbumsUseCase], then splits it into Pinned + More based on the list of
 * keys stored in [GetSettingsUseCase] (DataStore).
 *
 * Because the source is now a `Flow`, albums auto re-index when: media
 * permission is granted, a new photo/video appears, an item is favorited/trashed,
 * or an album cover is changed -- with no need to close and reopen the app.
 */
class AlbumsViewModel(
    observeAlbums: ObserveAlbumsUseCase,
    getSettings: GetSettingsUseCase,
    private val setPinnedAlbums: SetPinnedAlbumsUseCase,
    observeTrashItems: ObserveTrashItemsUseCase,
) : ViewModel() {
    private val albums: StateFlow<List<Album>?> = observeAlbums()
        .map<List<Album>, List<Album>?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    private val pinnedKeys: StateFlow<List<String>> = getSettings()
        .map { it.pinnedAlbumKeys ?: DEFAULT_PINNED_ALBUM_KEYS }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DEFAULT_PINNED_ALBUM_KEYS,
        )

    private val trashCount: StateFlow<Int> = observeTrashItems()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0,
        )

    val state: StateFlow<AlbumsUiState> =
        combine(albums, pinnedKeys, trashCount) { albums, pins, tc ->
            when {
                albums == null -> AlbumsUiState.Loading
                albums.isEmpty() && tc == 0 -> AlbumsUiState.Empty
                else -> buildContent(albums, pins, tc)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AlbumsUiState.Loading,
        )

    fun pin(key: String) {
        if (key == ALBUM_KEY_TRASH) return
        val current = pinnedKeys.value
        if (key in current) return
        viewModelScope.launch { setPinnedAlbums(current + key) }
    }

    fun unpin(key: String) {
        if (key in LOCKED_PIN_ALBUM_KEYS) return
        val current = pinnedKeys.value
        if (key !in current) return
        viewModelScope.launch { setPinnedAlbums(current - key) }
    }

    fun setOrder(keys: List<String>) {
        val sanitized = keys.filter { it != ALBUM_KEY_TRASH }
        viewModelScope.launch { setPinnedAlbums(sanitized) }
    }

    private fun buildContent(
        albums: List<Album>,
        pins: List<String>,
        trashCount: Int,
    ): AlbumsUiState.Content {
        val byKey = albums.associateBy { it.key }
        val ensuredPins = buildList {
            pins.forEach { if (it != ALBUM_KEY_TRASH) add(it) }
            LOCKED_PIN_ALBUM_KEYS.forEach { locked -> if (locked !in this) add(locked) }
        }
        val pinned = ensuredPins.mapNotNull { byKey[it] }
        val pinnedSet = pinned.map { it.key }.toHashSet()
        val more = albums
            .filter { it.key !in pinnedSet }
            .sortedBy { it.name.lowercase() }
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
