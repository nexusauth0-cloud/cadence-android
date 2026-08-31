package com.cadence.app.viewmodel

import com.cadence.app.ui.UserError
import com.cadence.core.media.PlaybackError
import com.cadence.core.media.PlaybackState
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5D §5: the media [PlaybackState] + resolution/offline signals map to a
 * single deliberate presentation model ([PlayerUiState]) — the surface the UI
 * consumes. These tests pin the RESOLVING/BUFFERING/PLAYING/PAUSED/UNAVAILABLE/
 * FAILED/OFFLINE contracts and their enabled actions.
 */
class PlayerViewStateMapperTest {

    private val ref = TrackRef(ProviderId("demo"), "a")

    private fun map(
        state: PlaybackState,
        preparing: Boolean = false,
        offline: Boolean = false,
        next: Boolean = false,
        previous: Boolean = false,
        error: UserError? = null,
    ) = PlayerViewStateMapper.map(
        state = state,
        prepareAheadOfBuffer = preparing,
        offlineAvailable = offline,
        hasQueuedNext = next,
        hasQueuedPrevious = previous,
        currentError = error,
    )

    @Test
    fun `idle and not preparing is Nothing no actions`() {
        val ui = map(PlaybackState.Idle)
        assertEquals(PlayerViewState.Nothing, ui.view)
        assertTrue(!ui.actions.canPlay && !ui.actions.canPause)
    }

    @Test
    fun `idle while preparing is Resolving`() {
        val ui = map(PlaybackState.Idle, preparing = true)
        assertEquals(PlayerViewState.Resolving, ui.view)
        assertEquals("Loading…", ui.view.status().headline)
    }

    @Test
    fun `buffering is Buffering`() {
        val ui = map(PlaybackState.Buffering(ref))
        assertEquals(PlayerViewState.Buffering, ui.view)
    }

    @Test
    fun `ready allows play and seek`() {
        val ui = map(PlaybackState.Ready(ref))
        assertTrue(ui.actions.canPlay)
        assertTrue(ui.actions.canSeek)
        assertTrue(!ui.actions.canPause)
    }

    @Test
    fun `playing shows offline variant and actions`() {
        val ui = map(PlaybackState.Playing(ref), offline = false, next = true, previous = true)
        assertEquals(PlayerViewState.Playing, ui.view)
        assertTrue(ui.actions.canPause)
        assertTrue(ui.actions.canSkipNext && ui.actions.canSkipPrevious)
    }

    @Test
    fun `playing from a download is honest OfflinePlaying`() {
        val ui = map(PlaybackState.Playing(ref), offline = true)
        assertTrue(ui.view is PlayerViewState.OfflinePlaying)
        assertTrue(ui.view.status().headline.contains("download"))
    }

    @Test
    fun `paused allows play`() {
        val ui = map(PlaybackState.Paused(ref))
        assertEquals(PlayerViewState.Paused, ui.view)
        assertTrue(ui.actions.canPlay)
        assertTrue(!ui.actions.canPause)
    }

    @Test
    fun `non retryable error maps to Unavailable`() {
        val ui = map(
            PlaybackState.Failed(ref, PlaybackError.Engine("err")),
            error = UserError("Not available offline.", retryable = false),
        )
        assertTrue(ui.view is PlayerViewState.Unavailable)
        assertTrue(!ui.actions.canRetry)
    }

    @Test
    fun `retryable failure maps to Failed with retry`() {
        val ui = map(
            PlaybackState.Failed(ref, PlaybackError.Engine("err")),
            error = UserError("Couldn't load.", retryable = true),
        )
        assertTrue(ui.view is PlayerViewState.Failed)
        assertTrue(ui.actions.canRetry)
    }

    @Test
    fun `ended offers replay and skip next`() {
        val ui = map(PlaybackState.Ended, next = true)
        assertEquals(PlayerViewState.Ended, ui.view)
        assertTrue(ui.actions.canPlay && ui.actions.canSkipNext)
    }

    @Test
    fun `all loaded states open the queue`() {
        assertTrue(map(PlaybackState.Ready(ref)).actions.canOpenQueue)
        assertTrue(map(PlaybackState.Playing(ref)).actions.canOpenQueue)
    }
}
