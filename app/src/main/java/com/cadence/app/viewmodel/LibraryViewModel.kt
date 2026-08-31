package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.core.database.PlayHistoryEntry
import com.cadence.core.database.PlaylistId
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.database.SavedTrack
import com.cadence.core.domain.ClearHistoryUseCase
import com.cadence.core.domain.CreatePlaylistUseCase
import com.cadence.core.domain.DeletePlaylistUseCase
import com.cadence.core.domain.ListPlaylistsUseCase
import com.cadence.core.domain.ObserveHistoryUseCase
import com.cadence.core.domain.ObserveLibraryUseCase
import com.cadence.core.domain.SaveTrackUseCase
import com.cadence.core.domain.UnsaveTrackUseCase
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Presenter for the Library region (§10): saved tracks, playlists, and history.
 * Owns local data reads/writes only — playback is delegated to the shared
 * [PlaybackViewModel]. UI stays honest: Albums/Artists are surfaced as not-yet
 * sections by the screen, never as fake counts.
 */
class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    private val saveTrack: SaveTrackUseCase,
    private val unsaveTrack: UnsaveTrackUseCase,
    observeHistory: ObserveHistoryUseCase,
    private val clearHistory: ClearHistoryUseCase,
    private val listPlaylists: ListPlaylistsUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    private val deletePlaylist: DeletePlaylistUseCase,
    private val observeIsSavedFor: (TrackRef) -> Flow<Boolean>,
) : ViewModel() {

    private val _savedTracks = MutableStateFlow<List<SavedTrack>>(emptyList())
    val savedTracks: StateFlow<List<SavedTrack>> = _savedTracks.asStateFlow()

    private val _history = MutableStateFlow<List<PlayHistoryEntry>>(emptyList())
    val history: StateFlow<List<PlayHistoryEntry>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistMetadata>>(emptyList())
    val playlists: StateFlow<List<PlaylistMetadata>> = _playlists.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<UserError?>(null)
    val error: StateFlow<UserError?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeLibrary().onEach { _savedTracks.value = it }.launchIn(viewModelScope)
        observeHistory().onEach { _history.value = it }.launchIn(viewModelScope)
        refreshPlaylists()
    }

    fun refreshPlaylists() {
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

    fun observeIsSaved(ref: TrackRef): Flow<Boolean> = observeIsSavedFor(ref)

    /** Save if absent, remove if present. */
    fun toggleSave(track: Track) {
        viewModelScope.launch {
            val currentlySaved = observeIsSavedFor(track.ref).first()
            try {
                if (currentlySaved) {
                    unsaveTrack(track)
                    _message.value = "Removed from your library."
                } else {
                    saveTrack(track)
                    _message.value = "Saved to your library."
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't update your library.", retryable = true)
            }
        }
    }

    fun save(track: Track) {
        viewModelScope.launch {
            try {
                saveTrack(track)
                _message.value = "Saved to your library."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't save this track.", retryable = true)
            }
        }
    }

    fun unsave(track: Track) {
        viewModelScope.launch {
            try {
                unsaveTrack(track)
                _message.value = "Removed from your library."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't update your library.", retryable = true)
            }
        }
    }

    fun requestCreatePlaylist(title: String) {
        viewModelScope.launch {
            try {
                createPlaylist(title)
                _playlists.value = listPlaylists()
                _message.value = "Created \"$title\"."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't create that playlist.", retryable = true)
            }
        }
    }

    fun requestDeletePlaylist(id: PlaylistId) {
        viewModelScope.launch {
            try {
                deletePlaylist(id)
                _playlists.value = listPlaylists()
                _message.value = "Deleted the playlist."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't delete that playlist.", retryable = true)
            }
        }
    }

    fun requestClearHistory() {
        viewModelScope.launch {
            try {
                val n = clearHistory()
                _message.value = if (n > 0) "Cleared $n play from history." else "History is already empty."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't clear history.", retryable = true)
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}