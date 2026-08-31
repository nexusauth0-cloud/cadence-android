package com.cadence.tests.e2e

import com.cadence.core.data.SpiCatalogSearchAdapter
import com.cadence.source.api.ProviderScope
import com.cadence.core.data.SpiResolutionAdapter
import com.cadence.core.domain.PlaybackOutcome
import com.cadence.core.domain.PreparePlaybackUseCase
import com.cadence.core.domain.SearchUseCase
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import com.cadence.source.api.DefaultProviderRegistry
import com.cadence.source.api.SourceError
import com.cadence.core.testing.FakePlaybackEngine
import com.cadence.core.testing.FakeSourceProvider
import com.cadence.core.testing.GoldenFixtures
import com.cadence.core.testing.MutableClock
import com.cadence.core.testing.SecondFakeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PRIMARY SPIKE PROOF (spike spec section 11).
 *
 * SEARCH -> Track -> SourceProvider(SPI) -> ResolvedSource -> PlaybackRequest
 *   -> PlaybackEngine port.
 *
 * Composition uses ONLY domain ports + fakes from :core:testing. No provider
 * implementation module is on the compile classpath (see build.gradle.kts), so
 * provider leakage into this test is impossible by construction.
 */
class E2ESpikeTest {

    private class Graph(clockIso: String) {
        val clock = MutableClock.at(clockIso)
        val demo = FakeSourceProvider(clock)
        val second = SecondFakeProvider(clock)
        val registry = DefaultProviderRegistry(listOf(demo, second))
        val search = SearchUseCase(SpiCatalogSearchAdapter(registry))
        val engine = FakePlaybackEngine()
        val prepare = PreparePlaybackUseCase(
            resolution = SpiResolutionAdapter(registry),
            engine = engine,
        )
    }

    @Test
    fun `search resolves and loads - golden path`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")

        // 1. SEARCH
        val page = g.search("alpha", scope = ProviderScope.Single(GoldenFixtures.DEMO))
        assertEquals(1, g.demo.totalSearchCalls)
        val track = page.items.single()
        val golden = GoldenFixtures.alphaTrack()
        assertEquals(golden.ref, track.ref)
        assertEquals(golden.title, track.title)
        assertEquals(golden.artists, track.artists)
        assertEquals(golden.durationMs, track.durationMs)
        assertEquals(golden.flags, track.flags)

        // 2. TRACK -> 3. RESOLVE -> 4. PLAYBACK REQUEST -> 5. ENGINE PORT
        val outcome = g.prepare(track)

        val started = assertIs<PlaybackOutcome.Started>(outcome)
        val goldenSource = GoldenFixtures.alphaResolvedSource(g.clock.instant())
        assertEquals(track.ref, started.request.track.ref)
        assertEquals(goldenSource.playbackUri, started.request.source.playbackUri)
        assertEquals(goldenSource.resolutionId, started.request.source.resolutionId)
        assertEquals(goldenSource.expiresAt, started.request.source.expiresAt)
        assertEquals(goldenSource.renewal, started.request.source.renewal)
        assertEquals(0L, started.request.startPositionMs)

        assertEquals(1, g.engine.loadCount)
        assertEquals(1, g.demo.resolveCountFor(GoldenFixtures.ALPHA_REF.nativeId))

        // Engine state machine remains fully test-driven.
        g.engine.simulateReady(started.request.track.ref)
        g.engine.simulatePlaying(started.request.track.ref, positionMs = 250)
        g.engine.tickPosition(750)
        assertIs<com.cadence.core.domain.PlaybackState.Playing>(g.engine.state.value).let { playing ->
            assertEquals(golden.ref, playing.ref)
        }
        assertEquals(1_000L, g.engine.positionMs.value)
    }

    @Test
    fun `pagination continues through cursor`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val page1 = g.search("alpha", scope = ProviderScope.Single(GoldenFixtures.DEMO))
        assertEquals(listOf("alpha-track"), page1.items.map { it.ref.nativeId })
        val page2 = g.search("alpha", cursor = page1.nextCursor, scope = ProviderScope.Single(GoldenFixtures.DEMO))
        assertEquals(listOf("alpha-b-side"), page2.items.map { it.ref.nativeId })
        assertNull(page2.nextCursor)
        assertEquals(2, g.demo.totalSearchCalls)
    }

    @Test
    fun `unavailable content fails before reaching the engine`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val outcome = g.prepare(com.cadence.core.model.Track(TrackRef(GoldenFixtures.DEMO, "unavailable/gone"), title = "Gone"))
        val failed = assertIs<PlaybackOutcome.Failed>(outcome)
        val err = assertIs<SourceError.Unavailable>(failed.failure.error)
        assertEquals(com.cadence.source.api.UnavailableReason.REMOVED, err.reason)
        assertNull(failed.failure.resolutionId)
        assertEquals(0, g.engine.loadCount)
    }

    @Test
    fun `rate-limited first attempt surfaces retryAfter then succeeds`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val ref = TrackRef(GoldenFixtures.DEMO, "rate-limited/rl-e2e")
        val track = com.cadence.core.model.Track(ref, title = "RL")

        val first = assertIs<PlaybackOutcome.Failed>(g.prepare(track))
        assertEquals(60_000L, first.retryAfter?.inWholeMilliseconds)
        assertEquals(0, g.engine.loadCount)

        // Deterministic second attempt (fake's one-shot limit) reaches playback.
        val second = assertIs<PlaybackOutcome.Started>(g.prepare(track))
        assertTrue(second.request.source.playbackUri.contains("/stream/rl-e2e"))
        assertEquals(1, g.engine.loadCount)
    }

    @Test
    fun `auth-required gates until login flips authState`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val track = com.cadence.core.model.Track(TrackRef(GoldenFixtures.DEMO, "auth-failed/locked"), title = "L")
        assertIs<PlaybackOutcome.Failed>(g.prepare(track)).let { f ->
            assertIs<SourceError.AuthRequired>(f.failure.error)
        }
        assertNull(g.demo.authState.value)
        g.demo.login("e2e-user")
        assertIs<PlaybackOutcome.Started>(g.prepare(track))
        assertEquals(1, g.engine.loadCount)
    }

    @Test
    fun `multi-provider routing sends refs to their own provider`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val outcome = g.prepare(
            com.cadence.core.model.Track(TrackRef(SecondFakeProvider.ID, "s-track"), title = "S"),
        )
        val started = assertIs<PlaybackOutcome.Started>(outcome)
        assertTrue(started.request.source.playbackUri.startsWith("https://second.fixture.demo/"))
        assertEquals("second-s-track", started.request.source.resolutionId)
    }

    @Test
    fun `unknown provider routes to typed Unsupported failure`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val outcome = g.prepare(
            com.cadence.core.model.Track(TrackRef(ProviderId("ghost"), "x"), title = "X"),
        )
        assertIs<SourceError.Unsupported>(
            assertIs<PlaybackOutcome.Failed>(outcome).failure.error!!,
        )
    }

    @Test
    fun `cancelling preparation stops resolution and never reaches engine`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val track = com.cadence.core.model.Track(TrackRef(GoldenFixtures.DEMO, "delayed/500-cxl"), title = "D")

        val job = async { g.prepare(track) }
        runCurrent()
        assertEquals(1, g.demo.activeWorkCount(), "resolution in flight")

        job.cancelAndJoin()

        // Cancellation surfaced as CancellationException inside the job, not a SourceError:
        // cancelAndJoin consumed it; prove no conversion by asserting engine untouched
        // and provider work fully unwound (spec section 12).
        assertEquals(0, g.engine.loadCount)
        assertEquals(0, g.demo.activeWorkCount())
    }

    @Test
    fun `cancellation identity is preserved through the whole chain`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val track = com.cadence.core.model.Track(TrackRef(GoldenFixtures.DEMO, "cancelled-immediately/z"), title = "C")
        val job = async { g.prepare(track) }
        runCurrent()
        job.cancel()
        val thrown = runCatching { job.await() }.exceptionOrNull()
        assertFailsWith<CancellationException> { throw thrown ?: error("expected cancellation") }
        assertTrue(thrown !is SourceError, "cancellation must never surface as SourceError")
    }

    @Test
    fun `delayed resolution completes on virtual time`() = runTest {
        val g = Graph("2026-08-26T00:00:00Z")
        val track = com.cadence.core.model.Track(TrackRef(GoldenFixtures.DEMO, "delayed/100-vt"), title = "V")
        val job = async { g.prepare(track) }
        runCurrent()
        assertTrue(job.isActive)
        advanceTimeBy(100)
        val outcome = job.await()
        assertIs<PlaybackOutcome.Started>(outcome)
    }
}
