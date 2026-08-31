package com.cadence.core.domain

import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val ref: TrackRef) : PlaybackState
    data class Ready(val ref: TrackRef) : PlaybackState
    data class Playing(val ref: TrackRef) : PlaybackState
    data class Paused(val ref: TrackRef) : PlaybackState
    data object Ended : PlaybackState
    data class Failed(val failure: PlaybackFailure) : PlaybackState
}

/**
 * Structured playback failure. Wraps a [SourceError] (typed) plus the
 * resolutionId for trace correlation — never a bare exception string.
 */
data class PlaybackFailure(
    val error: SourceError?,
    val resolutionId: String?,
    val message: String,
)

/**
 * Abstract playback boundary (spike spec section 7). The real media3-backed
 * engine arrives later behind this port; the spike proves the seam with a fake.
 *
 * Implementations receive ONLY what PlaybackRequest carries: URIs, headers,
 * metadata. Provider API objects, HTTP clients and Room entities are invisible here.
 */
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>

    suspend fun load(request: PlaybackRequest)
    fun play()
    fun pause()

    /** Releases resources; idempotent. */
    suspend fun release()
}
