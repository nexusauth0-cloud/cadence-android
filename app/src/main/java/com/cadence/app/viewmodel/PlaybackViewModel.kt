package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.app.ui.toUserError
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.database.QueueStore
import com.cadence.core.domain.AddToQueueUseCase
import com.cadence.core.domain.PlaylistSession
import com.cadence.core.domain.PlayNextUseCase
import com.cadence.core.domain.RecordPlayHistoryUseCase
import com.cadence.core.domain.SourceResolutionPort
import com.cadence.core.media.PlaybackCommand
import com.cadence.core.media.PlaybackState
import com.cadence.core.media.PlayerController
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared playback presenter (Phase 5D). Owns the single [PlayerController]
 * (ADR-0006 §1) plus the durable queue ([QueueStore], ADR-0003).
 *
 * Durable queue intent: the queue is the user's working set. A playlist run is
 * persisted as a `QueueKind.PLAYLIST` session with its source context so the
 * queue sheet can say "Playing from <playlist>" and restart resumes it. Direct
 * loads seed a single-item manual session.
 *
 * The UI observes [queue] for now-playing/upcoming/source context; the
 * [PlayerController] remains the source of truth for playback state.
 */
class PlaybackViewModel(
    private val controller: PlayerController,
    private val resolution: SourceResolutionPort,
    private val queueStore: QueueStore = InMemoryQueueStore(),
    private val downloadRepository: DownloadRepository? = null,
    private val recordHistory: RecordPlayHistoryUseCase? = null,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = controller.state

    val positionMs: StateFlow<Long> = controller.positionMs

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _playError = MutableStateFlow<UserError?>(null)
    val playError: StateFlow<UserError?> = _playError.asStateFlow()

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    private val _session = MutableStateFlow<PlaylistSession?>(null)
    val session: StateFlow<PlaylistSession?> = _session.asStateFlow()

    private val _sessionIndex = MutableStateFlow(-1)
    val sessionIndex: StateFlow<Int> = _sessionIndex.asStateFlow()

    /** Durable queue session (ADR-0003) observed by the queue sheet. */
    val queue: StateFlow<QueueSession?> = queueStore.observe()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /**
     * Offline availability of the currently-loaded track, for the honest
     * "available offline / playing from your download" indicator.
     */
    private val _currentOffline = MutableStateFlow<OfflineAvailability>(OfflineAvailability.NotDownloaded)
    val offlineForCurrent: StateFlow<OfflineAvailability> = _currentOffline.asStateFlow()

    /** Presentation state for the player screens (Phase 5D §5). */
    val playerUiState: StateFlow<PlayerUiState> = controller.state
        .map { playbackState ->
            mapToUi(
                state = playbackState,
                preparing = _preparing.value,
                offlineAvailable = _currentOffline.value is OfflineAvailability.Available,
                currentError = _playError.value,
                hasNext = hasNext(),
                hasPrevious = hasPrevious(),
            )
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, PlayerViewStateMapper.map(PlaybackState.Idle, false, false))

    /** Refs already recorded to history this process, to avoid double-recording. */
    private val recordedRefs = mutableSetOf<TrackRef>()

    private val addToQueueUC = AddToQueueUseCase(queueStore)
    private val playNextUC = PlayNextUseCase(queueStore)

    val isLoaded: Boolean get() = controller.state.value !is PlaybackState.Idle

    init {
        controller.state
            .onEach { s ->
                val track = _currentTrack.value ?: return@onEach
                val ref = when (s) {
                    is PlaybackState.Playing -> s.ref
                    is PlaybackState.Ready -> s.ref
                    else -> null
                }
                if (ref != null && ref == track.ref && recordedRefs.add(ref)) {
                    recordHistory?.invoke(track)
                }
            }
            .launchIn(viewModelScope)

        restoreQueue()
    }

    /** Restore a persisted queue session after process death (ADR-0003 rule 2). */
    private fun restoreQueue() {
        viewModelScope.launch {
            val restored = queueStore.get() ?: return@launch
            if (restored.tracks.isEmpty()) return@launch
            when (restored.kind) {
                QueueKind.PLAYLIST -> {
                    _sessionIndex.value = restored.currentIndex
                    launchLoad(trackFrom(restored.current!!))
                    _currentOffline.value = currentAvailabilityFor(restored.current!!.ref)
                }
                else -> {
                    _sessionIndex.value = 0
                    launchLoad(trackFrom(restored.current ?: restored.tracks.first()))
                    _currentOffline.value = currentAvailabilityFor((restored.current ?: restored.tracks.first()).ref)
                }
            }
        }
    }

    fun loadAndPlay(track: Track) {
        // Direct load: seed the durable queue with this track (restart resumes it).
        viewModelScope.launch {
            queueStore.save(
                QueueSession(
                    kind = QueueKind.MANUAL,
                    currentIndex = 0,
                    tracks = listOf(QueueItem(0, track.ref, track.toQueueMeta(), QueueAddedReason.SEED)),
                ),
            )
        }
        _session.value = null
        _sessionIndex.value = 0
        launchLoad(track)
    }

    private fun launchLoad(track: Track) {
        viewModelScope.launch {
            _playError.value = null
            _preparing.value = true
            try {
                val source = resolution.resolve(track.ref, ResolutionHints())
                controller.load(PlaybackRequest(track = track, source = source))
                controller.play()
                _currentTrack.value = track
                _currentOffline.value = currentAvailabilityFor(track.ref)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: SourceError) {
                _playError.value = e.toUserError()
            } catch (e: com.cadence.core.domain.SourceErrorUnavailable) {
                _playError.value = UserError("Not available offline.", retryable = false)
            } finally {
                _preparing.value = false
            }
        }
    }

    /**
     * Begin a playlist session at [session.startIndex], persisting the durable
     * queue intent with an optional source [contextLabel] (e.g. the playlist
     * name) for the "Playing from <playlist>" affordance.
     */
    fun playSession(session: PlaylistSession, contextLabel: String? = null) {
        persistPlaylistSession(session, contextLabel)
        _session.value = session
        _sessionIndex.value = session.startIndex
        session.tracks.getOrNull(session.startIndex)?.let(::launchLoad)
    }

    private fun persistPlaylistSession(session: PlaylistSession, contextLabel: String?) {
        viewModelScope.launch {
            if (session.tracks.isEmpty()) { queueStore.clear(); return@launch }
            queueStore.save(
                QueueSession(
                    kind = QueueKind.PLAYLIST,
                    continuationSerialized = contextLabel,
                    currentIndex = session.startIndex,
                    tracks = session.tracks.mapIndexed { i, t ->
                        QueueItem(i, t.ref, t.toQueueMeta(), QueueAddedReason.SEED)
                    },
                ),
            )
        }
    }

    fun play() = controller.play()

    fun pause() = controller.pause()

    fun togglePlayPause() {
        when (val s = controller.state.value) {
            is PlaybackState.Playing -> controller.pause()
            is PlaybackState.Ready,
            is PlaybackState.Paused,
            is PlaybackState.Ended -> controller.play()
            else -> Unit
        }
    }

    fun seekTo(positionMs: Long) = controller.command(PlaybackCommand.SeekTo(positionMs))

    // ── Audio normalization (ADR-0006 §6 effect chain) ────────────────

    private val _normalizeAudio = MutableStateFlow(false)
    val normalizeAudio: StateFlow<Boolean> = _normalizeAudio.asStateFlow()

    /** Toggle loudness normalization; routed through the controller effect chain. */
    fun setNormalization(enabled: Boolean) {
        _normalizeAudio.value = enabled
        controller.command(PlaybackCommand.SetNormalization(enabled))
    }

    /** Retry the failed current load. */
    fun retry() {
        _currentTrack.value?.let { launchLoad(it) }
    }

    fun skipToNext() {
        val session = _session.value
        val idx = _sessionIndex.value
        if (session != null && idx in 0 until session.tracks.lastIndex) {
            advanceTo(idx + 1)
            return
        }
        // Manual queue fallback (non-playlist session).
        val qs = queue.value
        if (qs != null && qs.kind != QueueKind.PLAYLIST && idx in 0 until qs.tracks.lastIndex) {
            advanceTo(qs.tracks[idx + 1].position)
            return
        }
        controller.command(PlaybackCommand.SkipToNext)
    }

    fun skipToPrevious() {
        val session = _session.value
        val idx = _sessionIndex.value
        if (session != null && idx > 0) {
            _sessionIndex.value = idx - 1
            persistCurrentIndex(idx - 1)
            launchLoad(session.tracks[idx - 1])
            return
        }
        controller.command(PlaybackCommand.SkipToPrevious)
    }

    private fun advanceTo(nextIndex: Int) {
        val session = _session.value
        if (session != null) {
            _sessionIndex.value = nextIndex
            persistCurrentIndex(nextIndex)
            launchLoad(session.tracks[nextIndex])
        } else {
            val qs = queue.value
            if (qs != null) {
                viewModelScope.launch { queueStore.save(qs.copy(currentIndex = nextIndex)) }
                _sessionIndex.value = nextIndex
                launchLoad(trackFrom(qs.tracks[nextIndex]))
            }
        }
    }

    private fun hasNext(): Boolean {
        val session = _session.value
        val idx = _sessionIndex.value
        if (session != null) return idx in 0 until session.tracks.lastIndex
        val qs = queue.value
        if (qs != null && qs.kind != QueueKind.PLAYLIST) return idx in 0 until qs.tracks.lastIndex
        return false
    }

    private fun hasPrevious(): Boolean = _sessionIndex.value > 0

    // ── Queue operations (durable intent) ────────────────────────────────

    fun addToQueue(track: Track) {
        viewModelScope.launch { addToQueueUC(track) }
    }

    fun playNext(track: Track) {
        viewModelScope.launch { playNextUC(track) }
    }

    /** Play the queue item at [position] (queue sheet tap). */
    fun playQueueItem(position: Int) {
        viewModelScope.launch {
            val qs = queue.value ?: return@launch
            val item = qs.tracks.getOrNull(position) ?: return@launch
            queueStore.save(qs.copy(currentIndex = position))
            _sessionIndex.value = position
            launchLoad(trackFrom(item))
            _currentOffline.value = currentAvailabilityFor(item.ref)
        }
    }

    /** Rebuild a display [Track] for queue items exposed to the UI. */
    fun trackFor(item: QueueItem): Track = trackFrom(item)

    fun removeAt(position: Int) {
        viewModelScope.launch {
            val removed = queueStore.removeAt(position)
            if (removed) reindexSessionFromStore()
        }
    }

    fun reorder(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            if (fromPosition == toPosition || !queueStore.move(fromPosition, toPosition)) return@launch
            reindexSessionFromStore()
        }
    }

    fun clearQueue() {
        viewModelScope.launch { queueStore.clear() }
        _session.value = null
        _sessionIndex.value = -1
    }

    /** After a store mutation, re-sync the session mirror (order/current index). */
    private suspend fun reindexSessionFromStore() {
        val qs = queue.value ?: return
        val tracks = qs.tracks.map { item -> trackFrom(item) }
        _sessionIndex.value = qs.currentIndex
        if (qs.kind == QueueKind.PLAYLIST) {
            _session.value = PlaylistSession(tracks = tracks, startIndex = qs.currentIndex.coerceAtLeast(0))
        }
        // Reload the current track if it changed as a result of the mutation.
        val current = qs.current
        if (current != null && _currentTrack.value?.ref != current.ref) {
            launchLoad(trackFrom(current))
            _currentOffline.value = currentAvailabilityFor(current.ref)
        }
    }

    private fun persistCurrentIndex(index: Int) {
        viewModelScope.launch {
            val qs = queue.value ?: return@launch
            queueStore.save(qs.copy(currentIndex = index))
        }
    }

    private suspend fun currentAvailabilityFor(ref: TrackRef): OfflineAvailability =
        downloadRepository?.offlineAvailability(ref) ?: OfflineAvailability.NotDownloaded

    private fun mapToUi(
        state: PlaybackState,
        preparing: Boolean,
        offlineAvailable: Boolean,
        currentError: UserError?,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): PlayerUiState = PlayerViewStateMapper.map(
        state = state,
        prepareAheadOfBuffer = preparing,
        offlineAvailable = offlineAvailable,
        hasQueuedNext = hasNext,
        hasQueuedPrevious = hasPrevious,
        currentError = currentError,
    )

    fun setExpanded(expanded: Boolean) {
        _expanded.value = expanded
    }

    fun dismissPlayError() {
        _playError.value = null
    }
}

/** Rebuild a display [Track] from a [QueueItem]'s cached metadata (no re-resolve). */
private fun QueueItem.toTrack(): Track =
    Track(
        ref = ref,
        title = meta.title ?: "Unknown",
        artists = meta.artist?.let { listOf(ArtistRef(it)) } ?: emptyList(),
    )

private fun trackFrom(item: QueueItem): Track = item.toTrack()

private fun Track.toQueueMeta(): QueueItemMeta =
    QueueItemMeta(
        title = title,
        artist = displayArtist(),
        artworkUrl = artwork?.variants?.maxByOrNull { (it.widthPx ?: 0) * (it.heightPx ?: 0) }?.url,
    )
