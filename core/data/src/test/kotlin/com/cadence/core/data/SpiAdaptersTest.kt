package com.cadence.core.data

import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.TrackRef
import com.cadence.source.api.DefaultProviderRegistry
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceError
import com.cadence.core.testing.GoldenFixtures
import com.cadence.core.testing.MutableClock
import com.cadence.core.testing.FakeSourceProvider
import com.cadence.core.testing.SecondFakeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpiAdaptersTest {

    private val clock = MutableClock.at("2026-08-26T00:00:00Z")

    @Test
    fun `single-scope search routes to the named provider only`() = runTest {
        val demo = FakeSourceProvider(clock)
        val second = SecondFakeProvider(clock)
        val adapter = SpiCatalogSearchAdapter(DefaultProviderRegistry(listOf(demo, second)))

        val page = adapter.search(SearchQuery("alpha", scope = com.cadence.source.api.ProviderScope.Single(GoldenFixtures.DEMO)))
        assertEquals(1, demo.totalSearchCalls)
        assertEquals(listOf("alpha-track"), page.items.map { it.ref.nativeId })
    }

    @Test
    fun `all-registered scope merges in registration order and tolerates partial failure`() = runTest {
        val demo = FakeSourceProvider(clock)
        val failing = FailingProvider(ProviderId("failing"), SourceError.NetworkUnreachable())
        val adapter = SpiCatalogSearchAdapter(DefaultProviderRegistry(listOf(demo, failing)))
        val page = adapter.search(SearchQuery("alpha"))
        assertEquals(1, page.items.size, "partial failure tolerated; healthy provider results kept")
        assertEquals("demo", page.items.single().ref.providerId.value)
    }

    @Test
    fun `all-fail surfaces the first typed error`() = runTest {
        val failingA = FailingProvider(ProviderId("fail-a"), SourceError.RateLimited(null))
        val failingB = FailingProvider(ProviderId("fail-b"), SourceError.RegionLocked())
        val adapter = SpiCatalogSearchAdapter(DefaultProviderRegistry(listOf(failingA, failingB)))
        val err = assertFailsWith<SourceError> { adapter.search(SearchQuery("x")) }
        assertIs<SourceError.RateLimited>(err)
    }

    @Test
    fun `resolution routes by providerId and rejects unknown ids as Unsupported`() = runTest {
        val second = SecondFakeProvider(clock)
        val adapter = SpiResolutionAdapter(DefaultProviderRegistry(listOf(second)))
        val src = adapter.resolve(TrackRef(SecondFakeProvider.ID, "abc"), ResolutionHints())
        assertEquals("second-abc", src.resolutionId)

        val err = assertFailsWith<SourceError.Unsupported> {
            adapter.resolve(TrackRef(ProviderId("ghost"), "abc"), ResolutionHints())
        }
        assertTrue(requireNotNull(err.message).contains("ghost"))
    }

    @Test
    fun `golden demo ref resolves through registry composition`() = runTest {
        val demo = FakeSourceProvider(clock)
        val adapter = SpiResolutionAdapter(DefaultProviderRegistry(listOf(demo)))
        val src = adapter.resolve(GoldenFixtures.ALPHA_REF, ResolutionHints())
        assertEquals(FakeSourceProvider.resolutionIdFor(GoldenFixtures.ALPHA_REF), src.resolutionId)
    }

    private class FailingProvider(
        override val id: ProviderId,
        private val error: SourceError,
    ) : com.cadence.source.api.SourceProvider {
        override val capabilities = setOf(com.cadence.source.api.Capability.SEARCH, com.cadence.source.api.Capability.RESOLVE)
        override val authState = kotlinx.coroutines.flow.MutableStateFlow<com.cadence.source.api.ProviderAuth?>(null)
        override suspend fun search(query: SearchQuery, cursor: com.cadence.source.api.PageCursor?): com.cadence.source.api.PagedResult<com.cadence.core.model.Track> =
            throw error
        override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): com.cadence.core.model.ResolvedSource =
            throw error
    }
}
