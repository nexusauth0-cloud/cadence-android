package com.cadence.core.data

import com.cadence.core.domain.CatalogSearchPort
import com.cadence.core.domain.SourceResolutionPort
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderRegistry
import com.cadence.source.api.ProviderScope
import com.cadence.source.api.SearchQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Port implementations bridging the SPI to the domain (spike spec section 3).
 * All provider knowledge stops at the registry boundary.
 */
class SpiCatalogSearchAdapter(
    private val registry: ProviderRegistry,
) : CatalogSearchPort {

    override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> =
        when (val scope = query.scope) {
            is ProviderScope.Single -> registry.require(scope.id).search(query, cursor)
            ProviderScope.AllRegistered -> searchAll(query, cursor)
        }

    private suspend fun searchAll(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> = coroutineScope {
        val providers = registry.all()
        require(providers.isNotEmpty()) { "no providers registered" }
        val outcomes = providers
            .map { provider -> async { runCatching { provider.search(query, cursor) } } }
            .map { it.await() }
        val successes = outcomes.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) {
            throw outcomes.firstNotNullOf { it.exceptionOrNull() }
        }
        // Registration order preserved; cross-provider ranking is post-spike.
        PagedResult(
            items = successes.flatMap { it.items },
            nextCursor = successes.firstNotNullOfOrNull { it.nextCursor },
        )
    }
}

class SpiResolutionAdapter(
    private val registry: ProviderRegistry,
) : SourceResolutionPort {

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource =
        registry.require(ref.providerId).resolve(ref, hints)
}
