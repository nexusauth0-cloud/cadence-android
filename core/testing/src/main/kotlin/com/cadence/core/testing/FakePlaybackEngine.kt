package com.cadence.core.testing

import com.cadence.core.domain.PlaybackEngine
import com.cadence.core.domain.PlaybackFailure
import com.cadence.core.domain.PlaybackState
import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections

/**
 * Deterministic fake of the playback boundary (spike spec section 7/10).
 * No timers, no audio: tests drive state transitions explicitly and assert on
 * recorded loads. Proves the engine port can be replaced wholesale.
 */
class FakePlaybackEngine : PlaybackEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs

    val loadedRequests: MutableList<PlaybackRequest> = Collections.synchronizedList(mutableListOf())
    var loadCount: Int = 0
        private set
    var playCount: Int = 0
        private set
    var pauseCount: Int = 0
        private set
    var released: Boolean = false
        private set

    override suspend fun load(request: PlaybackRequest) {
        check(!released) { "engine released" }
        loadCount++
        loadedRequests.add(request)
        _state.value = PlaybackState.Buffering(request.track.ref)
    }

    fun simulateReady(ref: TrackRef) {
        _state.value = PlaybackState.Ready(ref)
    }

    fun simulatePlaying(ref: TrackRef, positionMs: Long = 0) {
        _state.value = PlaybackState.Playing(ref)
        _positionMs.value = positionMs
    }

    fun tickPosition(ms: Long) {
        _positionMs.value += ms
    }

    fun simulatePaused(ref: TrackRef) {
        _state.value = PlaybackState.Paused(ref)
    }

    fun simulateEnded() {
        _state.value = PlaybackState.Ended
    }

    fun simulateFailed(failure: PlaybackFailure) {
        _state.value = PlaybackState.Failed(failure)
    }

    override fun play() {
        playCount++
    }

    override fun pause() {
        pauseCount++
    }

    override suspend fun release() {
        released = true
        _state.value = PlaybackState.Idle
    }
}
