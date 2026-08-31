package com.cadence.app.viewmodel

import com.cadence.app.ui.UserError
import com.cadence.core.media.PlaybackState

/**
 * Deliberate player UI state (Phase 5D §5). NOT a duplicate of the media
 * [PlaybackState] — it is a presentation mapping that combines the playback
 * state with resolution/preparation and offline signals into a single per-screen
 * model. The [com.cadence.core.media.PlayerController] remains the source of
 * truth; the UI never owns an ExoPlayer.
 *
 * Each state declares its visual treatment, available actions, accessibility
 * announcement, and retry behavior so screens render consistently rather than
 * collapsing every non-playing state into one spinner.
 */
sealed interface PlayerViewState {

    /** Nothing loaded yet. Mini-player hidden. */
    data object Nothing : PlayerViewState

    /** Resolution/preparation in progress, before the engine is buffering. */
    data object Resolving : PlayerViewState

    /** Engine buffering (state ref set). */
    data object Buffering : PlayerViewState

    /** Loaded and can be advanced from. */
    data object Ready : PlayerViewState

    data object Playing : PlayerViewState

    data object Paused : PlayerViewState

    data object Ended : PlayerViewState

    /** Honest "not available offline" / unavailable (never fake availability). */
    data class Unavailable(val message: String) : PlayerViewState

    /** Hard failure with a typed, user-safe message plus retry capability. */
    data class Failed(val error: UserError) : PlayerViewState

    /** Playing from a verified local download (offline indicator). */
    data class OfflinePlaying(val message: String) : PlayerViewState

    /** Offline and paused. */
    data class OfflinePaused(val message: String) : PlayerViewState
}

/** True while audio is genuinely playing (incl. offline), for row indicators. */
val PlayerViewState.isPlaying: Boolean
    get() = this is PlayerViewState.Playing || this is PlayerViewState.OfflinePlaying

/**
 * Which transport/retry actions are valid in a given view state. Screens use
 * these to enable/disable controls and to announce state via accessibility.
 */
data class PlayerViewActions(
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSeek: Boolean = false,
    val canRetry: Boolean = false,
    val canOpenQueue: Boolean = false,
)

/** Accessibility announcement + concise status line for the current state. */
data class PlayerViewStatus(
    val announcement: String,
    val headline: String,
)

/** Full presentation model: a view state plus the actions valid in it. */
data class PlayerUiState(
    val view: PlayerViewState,
    val actions: PlayerViewActions,
)

/** Turns a [PlayerViewState] into the per-state status lines (a11y + shown). */
fun PlayerViewState.status(): PlayerViewStatus = when (this) {
    PlayerViewState.Nothing -> PlayerViewStatus("Nothing playing", "Nothing playing")
    PlayerViewState.Resolving -> PlayerViewStatus("Getting a playable version", "Loading…")
    PlayerViewState.Buffering -> PlayerViewStatus("Buffering", "Loading…")
    PlayerViewState.Ready -> PlayerViewStatus("Ready to play", "Ready")
    PlayerViewState.Playing -> PlayerViewStatus("Playing", "Playing")
    PlayerViewState.Paused -> PlayerViewStatus("Paused", "Paused")
    PlayerViewState.Ended -> PlayerViewStatus("Finished", "Finished")
    is PlayerViewState.Unavailable -> PlayerViewStatus(this.message, this.message)
    is PlayerViewState.Failed -> PlayerViewStatus(this.error.message, this.error.message)
    is PlayerViewState.OfflinePlaying -> PlayerViewStatus(this.message, this.message)
    is PlayerViewState.OfflinePaused -> PlayerViewStatus(this.message, this.message)
}

object PlayerViewStateMapper {

    /**
     * Map the domain [PlaybackState] plus resolution flag into a presentation
     * model. `prepareAheadOfBuffer` covers the pre-buffering resolution window.
     * `offlineAvailable` refines PLAYING/PAUSED into the honest offline variants
     * (never claims offline when not true). `currentError` is used for the
     * typed FAILED/UNAVAILABLE states.
     */
    fun map(
        state: PlaybackState,
        prepareAheadOfBuffer: Boolean,
        offlineAvailable: Boolean,
        hasQueuedNext: Boolean = false,
        hasQueuedPrevious: Boolean = false,
        currentError: UserError? = null,
    ): PlayerUiState = when {
        prepareAheadOfBuffer && state is PlaybackState.Idle ->
            PlayerUiState(PlayerViewState.Resolving, PlayerViewActions(canSkipNext = hasQueuedNext, canSkipPrevious = hasQueuedPrevious, canOpenQueue = true))

        currentError != null && offlineAvailable.not() ->
            when {
                // A non-retryable source error means the content is genuinely not
                // available (e.g. "Not available offline") — shown honestly, not
                // as a recoverable failure with a spinner.
                !currentError.retryable ->
                    PlayerUiState(
                        PlayerViewState.Unavailable(currentError.message),
                        PlayerViewActions(canOpenQueue = true),
                    )
                else -> PlayerUiState(PlayerViewState.Failed(currentError), PlayerViewActions(canRetry = true, canOpenQueue = true))
            }

        else -> when (state) {
            is PlaybackState.Buffering -> PlayerUiState(PlayerViewState.Buffering, PlayerViewActions(canOpenQueue = true))
            is PlaybackState.Ready -> PlayerUiState(PlayerViewState.Ready, PlayerViewActions(canPlay = true, canSeek = true, canOpenQueue = true))
            is PlaybackState.Playing ->
                PlayerUiState(
                    if (offlineAvailable) PlayerViewState.OfflinePlaying("Playing from your download\nAvailable offline")
                    else PlayerViewState.Playing,
                    PlayerViewActions(
                        canPause = true,
                        canSkipNext = hasQueuedNext,
                        canSkipPrevious = hasQueuedPrevious,
                        canSeek = true,
                        canOpenQueue = true,
                    ),
                )
            is PlaybackState.Paused ->
                PlayerUiState(
                    if (offlineAvailable) PlayerViewState.OfflinePaused("Available offline")
                    else PlayerViewState.Paused,
                    PlayerViewActions(
                        canPlay = true,
                        canSkipNext = hasQueuedNext,
                        canSkipPrevious = hasQueuedPrevious,
                        canSeek = true,
                        canOpenQueue = true,
                    ),
                )
            PlaybackState.Ended -> PlayerUiState(PlayerViewState.Ended, PlayerViewActions(canPlay = true, canSkipNext = hasQueuedNext, canOpenQueue = true))
            is PlaybackState.Failed -> {
                val err = currentError ?: UserError("Playback hit a snag.", retryable = true)
                PlayerUiState(PlayerViewState.Failed(err), PlayerViewActions(canRetry = true, canOpenQueue = true))
            }
            PlaybackState.Idle -> PlayerUiState(PlayerViewState.Nothing, PlayerViewActions())
        }
    }
}
