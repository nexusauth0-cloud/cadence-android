package com.cadence.app

import com.cadence.app.di.AppDemoConfig
import com.cadence.app.di.AppGraph
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.core.database.InMemoryLibraryRepository
import com.cadence.core.domain.RecordPlayHistoryUseCase
import com.cadence.core.media.PlaybackState
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.core.testing.PlayerControllerFake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Playlist->session->PlayerController playback. The playlist is the durable
 * source of truth: [PlaybackViewModel.playSession] loads the start track and
 * [skipToNext]/[skipToPrevious] advance through the session by loading the
 * neighboring track (ADR-0003). History is recorded only on a genuinely
 * started playback; failed/cancelled attempts never reach history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun tr(id: String) = TrackRef(com.cadence.core.model.ProviderId("demo"), id)
    private fun track(id: String) = Track(ref = tr(id), title = "T$id", artists = listOf(ArtistRef("A")))

    // Native ids resolvable by the hermetic demo transport.
    private val A = "alpha-track"
    private val B = "bravo-track"
    private val C = "charlie-track"

    private fun demoGraph(controller: PlayerControllerFake, library: InMemoryLibraryRepository = InMemoryLibraryRepository()) =
        AppGraph(
            playerController = controller,
            providers = AppDemoConfig.providers(),
            defaultScope = AppDemoConfig.scope(),
            libraryRepository = library,
        )

    // Reuse the domain PlaylistSession builder directly to build a session.
    private fun session(vararg ids: String) = com.cadence.core.domain.PlaylistSession(
        tracks = ids.map(::track),
        startIndex = 0,
    )

    @Test
    fun `playSession loads the start track`() = runTest {
        val controller = PlayerControllerFake()
        val graph = demoGraph(controller)
        val playback = PlaybackViewModel(controller, graph.resolutionPort)

        playback.playSession(session(A, B, C))

        assertTrue(controller.state.value is PlaybackState.Playing)
        assertNotNull(playback.session.value)
        assertEquals("T$A", playback.currentTrack.value?.title)
    }

    @Test
    fun `skipToNext advances through the session`() = runTest {
        val controller = PlayerControllerFake()
        val graph = demoGraph(controller)
        val playback = PlaybackViewModel(controller, graph.resolutionPort)

        playback.playSession(session(A, B, C))
        playback.skipToNext()
        playback.skipToNext()

        assertEquals("T$C", playback.currentTrack.value?.title)
        assertEquals(2, playback.sessionIndex.value)

        // Past the end: falls back to the controller command, no crash, session intact.
        playback.skipToNext()
        assertEquals(2, playback.sessionIndex.value)
    }

    @Test
    fun `skipToPrevious backtracks within the session`() = runTest {
        val controller = PlayerControllerFake()
        val graph = demoGraph(controller)
        val playback = PlaybackViewModel(controller, graph.resolutionPort)

        playback.playSession(session(A, B, C))
        playback.skipToNext()
        playback.skipToPrevious()

        assertEquals("T$A", playback.currentTrack.value?.title)
        assertEquals(0, playback.sessionIndex.value)
    }

    @Test
    fun `direct load clears the session`() = runTest {
        val controller = PlayerControllerFake()
        val graph = demoGraph(controller)
        val playback = PlaybackViewModel(controller, graph.resolutionPort)

        playback.playSession(session(A, B))
        playback.loadAndPlay(track("delta-track"))

        assertNull(playback.session.value)
        assertEquals("Tdelta-track", playback.currentTrack.value?.title)
    }

    @Test
    fun `successful playback is recorded to history once`() = runTest {
        val controller = PlayerControllerFake()
        val library = InMemoryLibraryRepository()
        val graph = demoGraph(controller, library)
        val playback = PlaybackViewModel(
            controller = controller,
            resolution = graph.resolutionPort,
            recordHistory = RecordPlayHistoryUseCase(library),
        )

        val alpha = com.cadence.core.testing.GoldenFixtures.alphaTrack()
        playback.loadAndPlay(alpha)

        assertTrue(controller.state.value is PlaybackState.Playing)
        val history = library.observeHistory().first()
        assertEquals(listOf(com.cadence.core.testing.GoldenFixtures.ALPHA_REF.nativeId), history.map { it.ref.nativeId })

        // Pause -> play again does not double-record.
        playback.pause()
        playback.play()
        assertEquals(1, library.observeHistory().first().size)
    }

    @Test
    fun `failed resolution is not recorded to history`() = runTest {
        val controller = PlayerControllerFake()
        val library = InMemoryLibraryRepository()
        // A deliberately failing resolution port maps to a SourceError.
        val failingResolution = object : com.cadence.core.domain.SourceResolutionPort {
            override suspend fun resolve(
                ref: TrackRef,
                hints: com.cadence.core.model.ResolutionHints,
            ): com.cadence.core.model.ResolvedSource =
                throw com.cadence.source.api.SourceError.Unavailable(com.cadence.source.api.UnavailableReason.UNKNOWN)
        }
        val playback = PlaybackViewModel(
            controller = controller,
            resolution = failingResolution,
            recordHistory = RecordPlayHistoryUseCase(library),
        )

        playback.loadAndPlay(track("any"))

        assertNotNull(playback.playError.value)
        assertEquals(0, library.historyCount())
    }
}