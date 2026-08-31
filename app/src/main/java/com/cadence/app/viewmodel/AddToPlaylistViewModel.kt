package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.core.database.PlaylistId
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.domain.AddToPlaylistResult
import com.cadence.core.domain.AddToPlaylistUseCase
import com.cadence.core.domain.CreatePlaylistUseCase
import com.cadence.core.domain.ListPlaylistsUseCase
import com.cadence.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Add-to-playlist sheet presenter (used from Search and Library).
 *
 * Honest duplicate handling (§11): each playlist row reports its own
 * [AddToPlaylistResult] so "added", "already there" and "partly added" are
 * distinguishable and a duplicate is never silently re-inserted.
 */
class AddToPlaylistViewModel(
    private val listPlaylists: ListPlaylistsUseCase,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistMetadata>>(emptyList())
    val playlists: StateFlow<List<PlaylistMetadata>> = _playlists.asStateFlow()

    /** The tracks the user opened the sheet with. */
    private val _pendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val pendingTracks: StateFlow<List<Track>> = _pendingTracks.asStateFlow()

    /** Per-playlist outcome keyed by playlist id.value. */
    private val _results = MutableStateFlow<Map<String, AddToPlaylistResult>>(emptyMap())
    val results: StateFlow<Map<String, AddToPlaylistResult>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<UserError?>(null)
    val error: StateFlow<UserError?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _playlists.value = listPlaylists()
                _error.value = null
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't load your playlists.", retryable = true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun open(tracks: List<Track>) {
        _pendingTracks.value = tracks
        _results.value = emptyMap()
        _error.value = null
    }

    fun dismiss() {
        _pendingTracks.value = emptyList()
        _results.value = emptyMap()
    }

    fun addTo(playlistId: PlaylistId) {
        val tracks = _pendingTracks.value
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = addToPlaylist(playlistId, tracks)
                _results.value = _results.value + (playlistId.value to result)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't add to that playlist.", retryable = true)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Create a blank playlist and immediately add the pending tracks to it. */
    fun createAndAdd(title: String) {
        val tracks = _pendingTracks.value
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val id = createPlaylist(title)
                val result = addToPlaylist(id, tracks)
                _results.value = _results.value + (id.value to result)
                _playlists.value = listPlaylists()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't create that playlist.", retryable = true)
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}