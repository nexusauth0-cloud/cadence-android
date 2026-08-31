package com.cadence.core.domain

import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderScope
import com.cadence.source.api.SearchQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The single-source default is a DI-injected [ProviderScope] (ADR-0002): the
 * domain use case must never hard-code a provider id. These tests pin that the
 * injected default flows into the port query, and that an explicit caller-supplied
 * scope overrides it. Hermetic: uses a recording fake port, no providers, no network.
 */
class SearchUseCaseTest {

    private class RecordingCatalog : CatalogSearchPort {
        val scopes = mutableListOf<ProviderScope>()
        override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> {
            scopes += query.scope
            return PagedResult(emptyList())
        }
    }

    private val alpha = ProviderId("alpha")
    private val beta = ProviderId("beta")

    @Test
    fun `invoke without scope forwards the injected default scope`() = runTest {
        val catalog = RecordingCatalog()
        val useCase = SearchUseCase(catalog, ProviderScope.Single(alpha))

        useCase("hello")

        assertEquals(listOf<ProviderScope>(ProviderScope.Single(alpha)), catalog.scopes)
    }

    @Test
    fun `explicit scope overrides the injected default`() = runTest {
        val catalog = RecordingCatalog()
        val useCase = SearchUseCase(catalog, ProviderScope.Single(alpha))

        useCase("hello", scope = ProviderScope.Single(beta))

        assertEquals(listOf<ProviderScope>(ProviderScope.Single(beta)), catalog.scopes)
    }

    @Test
    fun `default constructor scope is all-registered`() = runTest {
        val catalog = RecordingCatalog()
        SearchUseCase(catalog).invoke("hello")
        assertEquals(listOf<ProviderScope>(ProviderScope.AllRegistered), catalog.scopes)
    }
}
