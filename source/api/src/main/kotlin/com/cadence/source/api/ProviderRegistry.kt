package com.cadence.source.api

import com.cadence.core.model.ProviderId

/**
 * Routing table for coexisting providers (spike spec section 5). Domain routes
 * purely by [TrackRef.providerId]; composition roots assemble implementations.
 */
interface ProviderRegistry {
    fun get(id: ProviderId): SourceProvider?
    fun require(id: ProviderId): SourceProvider
    fun all(): List<SourceProvider>
}

class DefaultProviderRegistry(
    providers: List<SourceProvider>,
) : ProviderRegistry {

    private val byId: Map<ProviderId, SourceProvider> =
        providers.associateBy { it.id }.also { map ->
            require(map.size == providers.size) { "duplicate provider ids registered" }
        }

    override fun get(id: ProviderId): SourceProvider? = byId[id]

    override fun require(id: ProviderId): SourceProvider =
        byId[id] ?: throw SourceError.Unsupported("unknown provider '$id'")

    override fun all(): List<SourceProvider> = byId.values.sortedBy { it.id.value }
}
