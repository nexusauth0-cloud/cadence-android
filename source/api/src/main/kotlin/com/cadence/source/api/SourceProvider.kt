package com.cadence.source.api

import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.StateFlow

/**
 * The provider-independent SPI (ADR-0002).
 *
 * Contract highlights:
 * - every method is suspend and one-shot; no streaming of partial results;
 * - cancellation is structured-concurrency owned by the CALLER and must
 *   propagate as CancellationException, never converted to [SourceError];
 * - implementations perform no retries beyond transport-level idempotent GETs
 *   owned by the network stack — policy retries belong to callers;
 * - only domain models cross this boundary. Provider DTOs never do.
 *
 * TRACK_DETAIL / RELATED / RADIO capabilities are deliberately NOT part of the
 * phase-1 surface (post-spike per spec section 20 Q7).
 */
interface SourceProvider {
    val id: ProviderId
    val capabilities: Set<Capability>

    /** Null while anonymous. Emits on login/logout; never contains credentials. */
    val authState: StateFlow<ProviderAuth?>

    /**
     * [cursor] continues a previous result's [PagedResult.nextCursor]. Null
     * starts a fresh query. (Recorded refinement vs the ADR-0002 sketch,
     * which left the continuation input channel unspecified.)
     */
    suspend fun search(query: SearchQuery, cursor: PageCursor? = null): PagedResult<Track>

    suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource
}
