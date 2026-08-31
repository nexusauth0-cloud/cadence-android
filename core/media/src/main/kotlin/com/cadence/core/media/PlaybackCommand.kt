package com.cadence.core.media

/**
 * High-level playback commands (ADR-0006 §7: command-pattern for
 * remote/session surfaces like Android Auto / Wear).
 *
 * The engine receives these through [PlayerController.command]; the
 * mapping to ExoPlayer API calls is the adapter's responsibility.
 */
sealed interface PlaybackCommand {
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data class SetSpeed(val speed: Float) : PlaybackCommand
    data class SetVolume(val volume: Float) : PlaybackCommand
    data class SetAudioAttributes(val attributes: AudioAttributes) : PlaybackCommand
    data class SetNormalization(val enabled: Boolean) : PlaybackCommand
    data object SkipToNext : PlaybackCommand
    data object SkipToPrevious : PlaybackCommand
    data object Stop : PlaybackCommand
}

/**
 * Audio attributes for routing and focus (ADR-0006 §5: duck-by-gain).
 */
data class AudioAttributes(
    val usage: Usage = Usage.MEDIA,
    val contentType: ContentType = ContentType.MUSIC,
) {
    enum class Usage { MEDIA, NOTIFICATION, ALARM, VOICE_COMMUNICATION }
    enum class ContentType { MUSIC, MOVIE, SPEECH }
}
