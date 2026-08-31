package com.cadence.app

import com.cadence.app.di.AppDemoConfig
import com.cadence.app.di.AppGraph
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.SearchUiState
import com.cadence.app.viewmodel.SearchViewModel
import com.cadence.core.media.PlaybackState
import com.cadence.core.testing.PlayerControllerFake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Vertical slice test: Search -> Track -> SourceProvider -> ResolvedSource ->
 * PlayerController -> Playback, driven by the real (hermetic) DEMO provider and
 * SPI adapters assembled in an explicit test [AppGraph]. This deliberately
 * constructs a DIFFERENT graph than production (demo provider, demo single
 * scope) so the test is fully offline: [AppDemoConfig] supplies the fake
 * provider and the test never touches the production YT Music/network path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchToPlaybackSliceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun testGraph(controller: PlayerControllerFake) = AppGraph(
        playerController = controller,
        providers = AppDemoConfig.providers(),
        defaultScope = AppDemoConfig.scope(),
    )

    @Test
    fun `searching alpha surfaces tracks and tapping one starts playback`() = runTest {
        val controller = PlayerControllerFake()
        val graph = testGraph(controller)
        val playback = PlaybackViewModel(controller, graph.resolutionPort)
        val search = SearchViewModel(graph.searchUseCase, onPlayTrack = playback::loadAndPlay)

        // Explicitly the paused demo-only single-source configuration (no network).
        assertEquals(setOf(com.cadence.source.demo.DemoProvider.ID), graph.registeredProviders)

        search.onQueryChange("alpha")
        advanceTimeBy(400)

        val state = search.uiState.value
        assertTrue("expected Results, got $state", state is SearchUiState.Results)
        val tracks = (state as SearchUiState.Results).tracks
        assertEquals(4, tracks.size)

        val first = tracks.first()
        assertEquals("Alpha Anthem", first.title)

        search.onResultTapped(first)

        val finalState = controller.state.value
        assertTrue("expected Playing, got $finalState", finalState is PlaybackState.Playing)
        assertEquals(first, playback.currentTrack.value)
    }

    @Test
    fun `blank query yields idle`() = runTest {
        val controller = PlayerControllerFake()
        val graph = testGraph(controller)
        val search = SearchViewModel(graph.searchUseCase, onPlayTrack = { })

        search.onQueryChange("")
        advanceTimeBy(400)

        assertEquals(SearchUiState.Idle, search.uiState.value)
    }
}

