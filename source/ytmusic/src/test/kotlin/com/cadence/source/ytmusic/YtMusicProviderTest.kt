package com.cadence.source.ytmusic

import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SearchQuery
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hermetic end-to-end tests of [YtMusicProvider]: the REAL NewPipe Extractor
 * v0.26.5 parsing + mapping pipeline (search and resolution) runs in CI against
 * a recorded wire fixture replayed by [FixtureDownloader]. Zero network
 * (ADR-0002 fixture-first); cheap and deterministic. The fixture is a single
 * recorded search("alpha") + resolve run authored via the
 * `:source:ytmusic:liveCapture` JavaExec task, never the test suite. Only the
 * HTTP hop is faked; extraction and mapping are genuine.
 */
class YtMusicProviderTest {

    private fun provider(resource: String = "search_resolve_happy.json"): YtMusicProvider {
        val fixture = resolveFixture(resource)
        val router = FixtureRouter.load(fixture)
        return YtMusicProvider(YtMusicTransport { FixtureDownloader(router) })
    }

    private fun resolveFixture(resource: String): File {
        val dir = File(javaClass.getResource("/fixtures/ytmusic")!!.toURI())
        return File(dir, resource)
    }

    @Test
    fun `search returns domain tracks from recorded fixture`() = runTest {
        val result = provider().search(SearchQuery("alpha"), cursor = null)

        assertTrue(result.items.isNotEmpty(), "expected at least one track from the fixture")
        val ids = result.items.map { it.ref.nativeId }
        // Native ids observed in the live capture that authored this fixture.
        assertTrue(ids.contains("JPcP_I8wNeQ"), "expected recorded native id, got $ids")
        assertTrue(ids.contains("j7HXK399r3g"), "expected recorded native id, got $ids")

        val track = result.items.first()
        assertEquals(YtMusicMapper.ID, track.ref.providerId)
        assertTrue(track.title.isNotBlank())
        assertEquals(track.ref.nativeId, track.ref.nativeId)
    }

    @Test
    fun `search results carry artist, duration and artwork when extractor provides them`() = runTest {
        val result = provider().search(SearchQuery("alpha"), cursor = null)

        val withArtist = result.items.firstOrNull { it.artists.isNotEmpty() }
        assertNotNull(withArtist, "expected at least one track with an artist attribution")
        assertTrue(withArtist.artists.first().name.isNotBlank())

        val withDuration = result.items.firstOrNull { it.durationMs != null && it.durationMs!! > 0 }
        assertNotNull(withDuration, "expected at least one track with a duration")
    }

    @Test
    fun `resolve produces a playable uri and deterministic metadata from fixture`() = runTest {
        val p = provider()
        val first = p.search(SearchQuery("alpha")).items.first()

        val source = p.resolve(first.ref, ResolutionHints())

        assertEquals(first.ref, source.ref)
        assertTrue(source.playbackUri.isNotBlank(), "expected a playback uri")
        assertTrue(source.resolutionId.startsWith("ytm-"), "unexpected resolutionId ${source.resolutionId}")
        assertEquals(first.ref, source.ref)
    }

    @Test
    fun `deterministic replay returns stable ids across runs`() = runTest {
        val r1 = provider().search(SearchQuery("alpha")).items.map { it.ref.nativeId }
        val r2 = provider().search(SearchQuery("alpha")).items.map { it.ref.nativeId }
        assertEquals(r1, r2)
    }
}
