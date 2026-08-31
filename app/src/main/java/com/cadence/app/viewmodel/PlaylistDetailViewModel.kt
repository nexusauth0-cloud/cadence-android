package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.core.database.PlaylistId
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.database.PlaylistTrack
import com.cadence.core.domain.GetPlaylistMetadataUseCase
import com.cadence.core.domain.GetPlaylistTracksUseCase
import com.cadence.core.domain.PlayPlaylistUseCase
import com.cadence.core.domain.PlaylistSession
import com.cadence.core.domain.RemoveTrackFromPlaylistUseCase
import com.cadence.core.domain.RenamePlaylistUseCase
import com.cadence.core.domain.ReorderPlaylistUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presenter for a single playlist detail screen: ordered tracks + metadata,
 * plus remove/rename/reorder and playlist->session playback. The playlist is
 * re-read after every mutation — it is the durable source of truth (ADR-0003).
 */
class PlaylistDetailViewModel(
    private val playlistId: PlaylistId,
    private val getMetadata: GetPlaylistMetadataUseCase,
    private val getTracks: GetPlaylistTracksUseCase,
    private val removeTrack: RemoveTrackFromPlaylistUseCase,
    private val reorder: ReorderPlaylistUseCase,
    private val rename: RenamePlaylistUseCase,
    private val playPlaylist: PlayPlaylistUseCase,
    private val onPlaySession: (PlaylistSession, String?) -> Unit,
) : ViewModel() {

    private val _metadata = MutableStateFlow<PlaylistMetadata?>(null)
    val metadata: StateFlow<PlaylistMetadata?> = _metadata.asStateFlow()

    private val _tracks = MutableStateFlow<List<PlaylistTrack>>(emptyList())
    val tracks: StateFlow<List<PlaylistTrack>> = _tracks.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<UserError?>(null)
    val error: StateFlow<UserError?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _metadata.value = getMetadata(playlistId)
                _tracks.value = getTracks(playlistId)
                if (_metadata.value == null) {
                    _error.value = UserError("This playlist no longer exists.", retryable = false)
                } else {
                    _error.value = null
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't load this playlist.", retryable = true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun play(track: PlaylistTrack) {
        val index = _tracks.value.indexOfFirst { it.ref == track.ref }.coerceAtLeast(0)
        playPlaylist(_tracks.value, index)?.let { onPlaySession(it, _metadata.value?.title) }
    }

    fun playAll() {
        playPlaylist(_tracks.value, 0)?.let { onPlaySession(it, _metadata.value?.title) }
    }

    /** Build a session starting at [startIndex]; null when nothing is playable. */
    fun buildSession(startIndex: Int = 0): PlaylistSession? = playPlaylist(_tracks.value, startIndex)

    fun remove(track: PlaylistTrack) {
        viewModelScope.launch {
            try {
                removeTrack(playlistId, track.ref)
                _tracks.value = getTracks(playlistId)
                _message.value = "Removed from this playlist."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't remove that track.", retryable = true)
            }
        }
    }

    /** Reorder (drag drops); position semantics are index-based and persisted. */
    fun move(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            if (fromIndex == toIndex) return@launch
            try {
                reorder(playlistId, fromIndex, toIndex)
                _tracks.value = getTracks(playlistId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't reorder the playlist.", retryable = true)
            }
        }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            val current = _metadata.value ?: return@launch
            try {
                rename(playlistId, title, current.description)
                _metadata.value = getMetadata(playlistId)
                _message.value = "Renamed."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't rename the playlist.", retryable = true)
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}