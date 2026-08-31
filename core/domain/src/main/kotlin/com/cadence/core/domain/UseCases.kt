package com.cadence.core.domain

import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.source.api.PagedResult
import com.cadence.source.api.SearchFilter
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceError
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

sealed interface PlaybackOutcome {
    data class Started(val request: PlaybackRequest) : PlaybackOutcome
    data class Failed(
        val failure: PlaybackFailure,
        val retryAfter: Duration? = null,
    ) : PlaybackOutcome
}

class SearchUseCase(
    private val catalog: CatalogSearchPort,
    private val defaultScope: com.cadence.source.api.ProviderScope = com.cadence.source.api.ProviderScope.AllRegistered,
) {
    /**
     * [scope] defaults to the DI-injected [defaultScope] so the composition root
     * pins the single-source provider without the UI ever naming a provider.
     */
    suspend operator fun invoke(
        text: String,
        filter: SearchFilter? = null,
        cursor: com.cadence.source.api.PageCursor? = null,
        scope: com.cadence.source.api.ProviderScope = defaultScope,
    ): PagedResult<com.cadence.core.model.Track> =
        catalog.search(SearchQuery(text, filter, scope), cursor)
}

/**
 * The architectural hinge of the spike (spec section 11): resolves a Track via
 * the resolution port and hands a fully-formed [PlaybackRequest] to the engine
 * port. Sees ONLY ports and models.
 *
 * Error contract:
 * - CancellationException always propagates untouched;
 * - SourceError goes through [policy] (bounded retries, no sleeping);
 * - any other throwable propagates raw — no catch-all conversion (quality rule §17).
 */
class PreparePlaybackUseCase(
    private val resolution: SourceResolutionPort,
    private val engine: PlaybackEngine,
    private val policy: RecoveryPolicy = DefaultRecoveryPolicy(),
) {

    suspend operator fun invoke(
        track: Track,
        hints: ResolutionHints = ResolutionHints(),
        startPositionMs: Long = 0,
    ): PlaybackOutcome {
        var attempt = 1
        while (true) {
            try {
                val source = resolution.resolve(track.ref, hints)
                val request = PlaybackRequest(
                    track = track,
                    source = source,
                    startPositionMs = startPositionMs,
                )
                engine.load(request)
                return PlaybackOutcome.Started(request)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: SourceError) {
                when (val decision = policy.decide(e, attempt)) {
                    is RecoveryPolicy.Decision.RetryNow -> { attempt++; continue }
                    is RecoveryPolicy.Decision.RetryIn ->
                        return PlaybackOutcome.Failed(failureOf(track, e), decision.delay)
                    RecoveryPolicy.Decision.GiveUp ->
                        return PlaybackOutcome.Failed(failureOf(track, e))
                }
            }
        }
    }

    private fun failureOf(track: Track, e: SourceError): PlaybackFailure =
        PlaybackFailure(error = e, resolutionId = null, message = "${track.ref}: ${e.message}")
}
