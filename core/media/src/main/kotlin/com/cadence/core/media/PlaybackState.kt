package com.cadence.core.media

import com.cadence.core.model.TrackRef

/**
 * Playback state machine (ADR-0006 §4: no polling loops — state is a flow).
 *
 * Transitions:
 *   Idle → Buffering → Ready → Playing ↔ Paused
 *                                  ↓
 *                               Ended
 *   any → Failed (terminal until next load)
 */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val ref: TrackRef) : PlaybackState
    data class Ready(val ref: TrackRef) : PlaybackState
    data class Playing(val ref: TrackRef) : PlaybackState
    data class Paused(val ref: TrackRef) : PlaybackState
    data object Ended : PlaybackState
    data class Failed(val ref: TrackRef?, val error: PlaybackError) : PlaybackState
}

/**
 * Structured playback error. Never a bare exception string (ADR-0006 §3).
 */
sealed interface PlaybackError {
    data class Source(val type: String, val message: String) : PlaybackError
    data class Engine(val message: String, val cause: Throwable? = null) : PlaybackError
    data class AudioFocus(val message: String) : PlaybackError
}
