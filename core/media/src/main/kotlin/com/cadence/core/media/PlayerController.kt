package com.cadence.core.media

import com.cadence.core.model.PlaybackRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Player controller port (ADR-0006).
 *
 * Exactly one logical application player per process — no second ExoPlayer
 * instance, no polling watchdog, no string-based error sniffing, no hidden
 * recovery loops. Recovery policy is explicit and lives in `:core:domain`.
 *
 * Media3/ExoPlayer types must NEVER leak across this boundary. The adapter
 * lives in a separate module and translates domain types → ExoPlayer API.
 *
 * This port is pure JVM — no Android, no Media3, no ExoPlayer dependency.
 */
interface PlayerController {

    /** Current playback state. Observers subscribe; no polling. */
    val state: StateFlow<PlaybackState>

    /** Current position in ms. Updated by the engine at its own cadence. */
    val positionMs: StateFlow<Long>

    /** Player events (non-state-change signals). */
    val events: SharedFlow<PlayerEvent>

    /**
     * Load and prepare a source for playback. Transitions:
     *   Idle/Ended/Failed → Buffering → Ready
     * Does NOT automatically start playing — call [play] after Ready.
     */
    suspend fun load(request: PlaybackRequest)

    /** Start/resume playback. Requires state = Ready or Paused. */
    fun play()

    /** Pause playback. Requires state = Playing. */
    fun pause()

    /** Send a command (seek, skip, etc.). */
    fun command(cmd: PlaybackCommand)

    /** Set the active audio effect chain. Must be called before [load]. */
    fun setEffectChain(chain: AudioEffectChain)

    /** Release all resources. Idempotent. After release, controller cannot be reused. */
    suspend fun release()

    /** Whether the controller has been released. */
    val isReleased: Boolean
}
