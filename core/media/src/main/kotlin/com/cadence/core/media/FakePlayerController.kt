package com.cadence.core.media

import com.cadence.core.model.PlaybackRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [PlayerController] for JVM tests.
 * Records all interactions for assertion; configurable behavior.
 */
class FakePlayerController : PlayerController {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var _isReleased = false
    override val isReleased: Boolean get() = _isReleased

    /** Recorded load requests. */
    val loadRequests = mutableListOf<PlaybackRequest>()

    /** Recorded play calls. */
    var playCount = 0; private set

    /** Recorded pause calls. */
    var pauseCount = 0; private set

    /** Recorded commands. */
    val commands = mutableListOf<PlaybackCommand>()

    /** Recorded effect chains. */
    val effectChains = mutableListOf<AudioEffectChain>()

    /** If set, [load] will transition to this state instead of buffering. */
    var loadError: PlaybackError? = null

    /** Virtual time tick for advancing positionMs in tests. */
    fun advancePosition(ms: Long) {
        _positionMs.value += ms
    }

    /** Simulate state transition (test helper). */
    fun setState(newState: PlaybackState) {
        _state.value = newState
    }

    override suspend fun load(request: PlaybackRequest) {
        check(!_isReleased) { "controller is released" }
        loadRequests.add(request)
        if (loadError != null) {
            _state.value = PlaybackState.Failed(request.track.ref, loadError!!)
        } else {
            _state.value = PlaybackState.Buffering(request.track.ref)
            _state.value = PlaybackState.Ready(request.track.ref)
        }
    }

    override fun play() {
        check(!_isReleased) { "controller is released" }
        playCount++
        val current = _state.value
        if (current is PlaybackState.Ready || current is PlaybackState.Paused) {
            _state.value = PlaybackState.Playing(
                when (current) {
                    is PlaybackState.Ready -> current.ref
                    is PlaybackState.Paused -> current.ref
                    else -> return
                }
            )
        }
    }

    override fun pause() {
        check(!_isReleased) { "controller is released" }
        pauseCount++
        val current = _state.value
        if (current is PlaybackState.Playing) {
            _state.value = PlaybackState.Paused(current.ref)
        }
    }

    override fun command(cmd: PlaybackCommand) {
        check(!_isReleased) { "controller is released" }
        commands.add(cmd)
    }

    override fun setEffectChain(chain: AudioEffectChain) {
        effectChains.add(chain)
    }

    override suspend fun release() {
        _isReleased = true
        _state.value = PlaybackState.Idle
    }
}
