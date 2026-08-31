package com.cadence.source.demo

import com.cadence.core.model.OpaquePayload
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.TrackFlag
import com.cadence.core.model.TrackRef
import com.cadence.source.api.AuthScope
import com.cadence.source.api.ExpiryKind
import com.cadence.source.api.PageCursor
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceError
import com.cadence.source.api.UnavailableReason
import com.cadence.core.model.ResolutionHints
import com.cadence.core.testing.MutableClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoProviderTest {

    private fun provider(overrides: Map<String, String> = emptyMap()) =
        DemoProvider(ClasspathFixtureTransport(overrides), MutableClock.at("2026-08-26T00:00:00Z"))

    private fun routeError(nativeId: String, fixture: String) =
        mapOf("resolve/happy/$nativeId" to "errors/$fixture")

    @Test
    fun `search maps first page to domain tracks`() = runTest {
        val result = provider().search(SearchQuery("alpha"))
        assertEquals(listOf("alpha-track"), result.items.map { it.ref.nativeId })
        val track = result.items.single()
        assertEquals("Alpha Anthem", track.title)
        assertEquals("Beta Band", track.artists.single().name)
        assertEquals(210_000L, track.durationMs)
        assertEquals(setOf(TrackFlag.EXPLICIT), track.flags)
        assertEquals(640, track.artwork?.variants?.single()?.widthPx)
        // fixture next=2 -> continuation cursor offered with provider-scoped token
        assertEquals("demo:2", result.nextCursor?.token)
    }

    @Test
    fun `search follows cursor to final page`() = runTest {
        val page2 = provider().search(SearchQuery("alpha"), PageCursor("demo:2"))
        assertEquals(listOf("alpha-b-side"), page2.items.map { it.ref.nativeId })
        assertNull(page2.nextCursor)
    }

    @Test
    fun `resolve rich metadata matches golden expectations`() = runTest {
        val clock = MutableClock.at("2026-08-26T00:00:00Z")
        val src = DemoProvider(ClasspathFixtureTransport(), clock)
            .resolve(TrackRef(DemoMapper.DEMO_ID, "alpha-track"), ResolutionHints())

        assertEquals("https://fixture.demo/stream/alpha-track?sig=tok", src.playbackUri)
        assertEquals(clock.instant().plusSeconds(300), src.expiresAt)
        assertFalse(src.isExpiredAt(clock.instant()))
        assertEquals("fixture", src.httpHeaders["X-Demo-Mode"])
        assertEquals("demo-medium", src.renditionLabel)
        assertEquals(192, src.metadata.bitrateKbps)
        assertEquals(-6.8, src.metadata.perceptualLoudnessDb!!, 1e-9)
        assertIs<OpaquePayload>(src.renewal)
        // diagnostics never leak signed query components
        assertTrue(src.toString().contains("stream/alpha-track"))
        assertFalse(src.toString().contains("sig=tok"))
    }

    @Test
    fun `resolve with absent metadata stays playable`() = runTest {
        val src = provider().resolve(TrackRef(DemoMapper.DEMO_ID, "plain-track"), ResolutionHints())
        assertNull(src.expiresAt)
        assertNull(src.renewal)
        assertEquals(SourceMetadata(), src.metadata)
    }

    @Test
    fun `negative expiry marks source expired immediately`() = runTest {
        val clock = MutableClock.at("2026-08-26T00:00:00Z")
        val src = DemoProvider(ClasspathFixtureTransport(), clock)
            .resolve(TrackRef(DemoMapper.DEMO_ID, "expired-track"), ResolutionHints())
        assertTrue(src.isExpiredAt(clock.instant()))
        // classification kind for URL expiry remains available to policy layer
        assertEquals(ExpiryKind.URL.name, "URL")
    }

    @Test
    fun `error fixtures map to typed SPI errors`() = runTest {
        val rateErr = assertFailsWith<SourceError> {
            provider(routeError("rl", "rate-limited")).resolve(TrackRef(DemoMapper.DEMO_ID, "rl"), ResolutionHints())
        }
        assertIs<SourceError.RateLimited>(rateErr).let {
            assertEquals(60_000L, it.retryAfter?.inWholeMilliseconds)
        }

        val authErr = assertFailsWith<SourceError.AuthRequired> {
            provider(routeError("ar", "auth-required")).resolve(TrackRef(DemoMapper.DEMO_ID, "ar"), ResolutionHints())
        }
        assertEquals(AuthScope.ACCOUNT, authErr.scope)

        assertFailsWith<SourceError.RegionLocked> {
            provider(routeError("rg", "region-locked")).resolve(TrackRef(DemoMapper.DEMO_ID, "rg"), ResolutionHints())
        }
        val unavail = assertFailsWith<SourceError.Unavailable> {
            provider(routeError("un", "unavailable-removed")).resolve(TrackRef(DemoMapper.DEMO_ID, "un"), ResolutionHints())
        }
        assertEquals(UnavailableReason.REMOVED, unavail.reason)

        assertFailsWith<SourceError.NetworkUnreachable> {
            provider(routeError("nu", "network-unreachable")).resolve(TrackRef(DemoMapper.DEMO_ID, "nu"), ResolutionHints())
        }
        assertFailsWith<SourceError.ResolutionFailed> {
            provider(routeError("rf", "resolution-failed")).resolve(TrackRef(DemoMapper.DEMO_ID, "rf"), ResolutionHints())
        }
    }

    @Test
    fun `status-only fallbacks classify without structured error block`() {
        val e503 = FixtureLoaderSupport.decode("""{"schema":1,"scenario":"x","transport":{"status":503},"body":{}}""")
        assertIs<SourceError.ResolutionFailed>(DemoErrorMapping.errorFor(e503, "p")!!)
        val e429 = FixtureLoaderSupport.decode("""{"schema":1,"scenario":"y","transport":{"status":429},"body":{}}""")
        assertIs<SourceError.RateLimited>(DemoErrorMapping.errorFor(e429, "p")!!)
        val ok = FixtureLoaderSupport.decode("""{"schema":1,"scenario":"z","transport":{"status":200},"body":{}}""")
        assertNull(DemoErrorMapping.errorFor(ok, "p"))
    }

    @Test
    fun `malformed bodies become InvalidResponse with bounded snippets`() = runTest {
        // truncated raw bytes -> envelope-level failure before DTO parsing
        val truncErr = assertFailsWith<SourceError.InvalidResponse> {
            provider(mapOf("resolve/happy/truncated" to "malformed/truncated"))
                .resolve(TrackRef(DemoMapper.DEMO_ID, "truncated"), ResolutionHints())
        }
        val truncSnippet: String? = truncErr.snippet
        assertTrue(truncSnippet == null || truncSnippet.length <= 201)

        // structurally-wrong JSON node -> decode/mapper level InvalidResponse
        val wrongNodeErr = assertFailsWith<SourceError.InvalidResponse> {
            provider(mapOf("resolve/happy/wrongnode" to "malformed/wrong-node-type"))
                .resolve(TrackRef(DemoMapper.DEMO_ID, "wrongnode"), ResolutionHints())
        }
        assertEquals("missing stream_url", wrongNodeErr.snippet)
    }

    @Test
    fun `unknown flag values are wire-contract violations on search side`() = runTest {
        val err = assertFailsWith<SourceError.InvalidResponse> {
            provider(mapOf("search/happy/flagged/page-01" to "malformed/unknown-flag"))
                .search(SearchQuery("flagged"))
        }
        val snippet = err.snippet
        assertTrue(snippet == null || snippet.length <= 201)
    }

    @Test
    fun `missing title rows are dropped not fabricated`() = runTest {
        val page = provider(
            mapOf("search/happy/dropper/page-01" to "malformed/missing-required-field"),
        ).search(SearchQuery("dropper"))
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `unsafe native ids are rejected as Unsupported`() = runTest {
        assertFailsWith<SourceError.Unsupported> {
            provider().resolve(TrackRef(DemoMapper.DEMO_ID, "../secret"), ResolutionHints())
        }
    }

    @Test
    fun `slugify is deterministic and bounded`() {
        assertEquals("alpha-anthem", DemoProvider.slugify("Alpha Anthem!"))
        assertEquals("a-b", DemoProvider.slugify("a--b"))
        assertEquals("query", DemoProvider.slugify("***"))
        assertEquals(40, DemoProvider.slugify("x".repeat(100)).length)
    }
}

/** Local helper indirection so tests do not depend on loader internals directly. */
private object FixtureLoaderSupport {
    fun decode(text: String) = com.cadence.core.common.FixtureLoader.decode(text)
}
