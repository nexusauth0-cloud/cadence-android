package com.cadence.core.media

/**
 * Minimal player event contract (ADR-0006 §4: widgets/UI subscribe to
 * session/state flows, not polling loops).
 */
sealed interface PlayerEvent {
    /** Current playback position updated at engine-owned cadence. */
    data class PositionChanged(val positionMs: Long) : PlayerEvent

    /** Audio session ID changed (for visualizers, effects chain). */
    data class AudioSessionChanged(val sessionId: Int) : PlayerEvent

    /** Metadata changed mid-playback (e.g., live stream metadata update). */
    data class MetadataChanged(val title: String?, val artist: String?) : PlayerEvent

    /** Error occurred but playback may continue (non-fatal). */
    data class Error(val type: ErrorType, val message: String) : PlayerEvent

    enum class ErrorType { RENDERER, TIMEOUT, UNEXPECTED }
}
