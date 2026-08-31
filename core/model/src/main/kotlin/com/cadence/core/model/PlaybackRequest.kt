package com.cadence.core.model

/** Audio processing requirements handed to the engine (feeds ADR-0006 chain). */
data class AudioTraits(
    val normalize: Boolean = false,
    val targetLufs: Double? = null,
)

/**
 * The single argument crossing the playback boundary (spike spec section 7).
 * Contains zero provider/network/UI types: the engine receives display
 * metadata plus everything needed to open and play the resolved rendition.
 */
data class PlaybackRequest(
    val track: Track,
    val source: ResolvedSource,
    val startPositionMs: Long = 0,
    val playWhenReady: Boolean = true,
    val audioTraits: AudioTraits = AudioTraits(),
)
