package com.cadence.app.viewmodel

import com.cadence.app.ui.UserError
import com.cadence.core.media.PlaybackError

/**
 * Maps [PlaybackError] (core:media) to user-facing copy + retry capability.
 * Lives in the `viewmodel` adapter package — the single app boundary allowed to
 * depend on core:media — so screens/components never import the player module
 * (rule Z). The UI renders only the resulting [UserError], never exception text.
 */
fun PlaybackError.toUserError(): UserError = when (this) {
    is PlaybackError.Source -> UserError("A source problem with this track.", retryable = true)
    is PlaybackError.Engine -> UserError("Playback hit a snag.", retryable = true)
    is PlaybackError.AudioFocus -> UserError("Sound paused — a call or notification took focus.", retryable = false)
}
