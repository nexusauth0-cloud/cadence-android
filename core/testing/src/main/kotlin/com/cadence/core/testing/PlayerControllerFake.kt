package com.cadence.core.testing

import com.cadence.core.media.AudioEffect
import com.cadence.core.media.AudioEffectChain
import com.cadence.core.media.AudioAttributes
import com.cadence.core.media.PlaybackCommand
import com.cadence.core.media.PlaybackError
import com.cadence.core.media.PlaybackState
import com.cadence.core.media.PlayerController
import com.cadence.core.media.PlayerEvent
import com.cadence.core.model.PlaybackRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal fake [PlayerController] for domain-level tests.
 * Simpler than `:core:media`'s FakePlayerController; does not record all interactions.
 */
class PlayerControllerFake : PlayerController {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var _released = false
    override val isReleased: Boolean get() = _released

    var loadCount = 0; private set
    var playCount = 0; private set
    var pauseCount = 0; private set
    var lastNormalizationCommand: Boolean? = null

    override suspend fun load(request: PlaybackRequest) {
        loadCount++
        _state.value = PlaybackState.Buffering(request.track.ref)
        _state.value = PlaybackState.Ready(request.track.ref)
    }

    override fun play() {
        playCount++
        val s = _state.value
        if (s is PlaybackState.Ready) _state.value = PlaybackState.Playing(s.ref)
    }

    override fun pause() {
        pauseCount++
        val s = _state.value
        if (s is PlaybackState.Playing) _state.value = PlaybackState.Paused(s.ref)
    }

    override fun command(cmd: PlaybackCommand) {
        when (cmd) {
            is PlaybackCommand.SetNormalization -> lastNormalizationCommand = cmd.enabled
            else -> {}
        }
    }
    override fun setEffectChain(chain: AudioEffectChain) {}

    override suspend fun release() {
        _released = true
        _state.value = PlaybackState.Idle
    }

    fun setState(s: PlaybackState) { _state.value = s }
}
