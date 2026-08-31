package com.cadence.core.testing

import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.TrackRef
import com.cadence.source.api.Capability
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderAuth
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock

private val SECOND = ProviderId("second")

/**
 * Minimal second provider proving registry-based multi-provider routing in E2E
 * (spike spec section 10): refs route by providerId to different sources of truth.
 */
class SecondFakeProvider(
    clock: Clock,
) : SourceProvider {

    private val fake = FakeSourceProvider(clock, SECOND)

    override val id: ProviderId = SECOND
    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE)

    private val _authState = MutableStateFlow<ProviderAuth?>(null)
    override val authState: StateFlow<ProviderAuth?> = _authState

    override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<com.cadence.core.model.Track> =
        PagedResult(items = listOf(fake.canonicalTrack(query.text)))

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource =
        // Distinct host proves the resolved source came from THIS provider.
        ResolvedSource(
            ref = TrackRef(id, ref.nativeId),
            playbackUri = "https://second.fixture.demo/stream/${ref.nativeId}",
            resolutionId = "second-${ref.nativeId}",
            expiresAt = null,
            renewal = null,
            renditionLabel = "second-basic",
        )

    companion object {
        val ID = SECOND
        fun cursor(): PageCursor = PageCursor("$SECOND:page-2")
    }
}
