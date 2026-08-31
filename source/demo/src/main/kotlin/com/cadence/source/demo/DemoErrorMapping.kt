package com.cadence.source.demo

import com.cadence.core.common.FixtureEnvelope
import com.cadence.source.api.AuthScope
import com.cadence.source.api.ExpiryKind
import com.cadence.source.api.SourceError
import kotlin.time.Duration.Companion.seconds
import com.cadence.source.api.UnavailableReason

/**
 * Deterministic mapping from fixture envelope outcomes to typed SPI errors
 * (spike spec section 8). The structured `error` block wins over status codes;
 * statuses are only a fallback.
 */
object DemoErrorMapping {

    fun errorFor(envelope: FixtureEnvelope, context: String): SourceError? {
        envelope.error?.let { err ->
            return when (err.type) {
                "rate_limited" -> SourceError.RateLimited(
                    retryAfter = err.retryAfterSeconds?.let { it.toLong().seconds },
                )
                "auth_required" -> SourceError.AuthRequired(AuthScope.ACCOUNT)
                "region_locked" -> SourceError.RegionLocked()
                "unavailable" -> SourceError.Unavailable(err.detail?.let { reason ->
                    UnavailableReason.entries.firstOrNull { it.name == reason }
                } ?: UnavailableReason.UNKNOWN)
                "network_unreachable" -> SourceError.NetworkUnreachable()
                "resolution_failed" -> SourceError.ResolutionFailed()
                else -> SourceError.InvalidResponse(context, "unknown fixture error type '${err.type}'")
            }
        }
        return when {
            envelope.transport.status == 429 -> SourceError.RateLimited(retryAfter = null)
            envelope.transport.status >= 500 -> SourceError.ResolutionFailed()
            else -> null
        }
    }

    @Suppress("unused")
    private val expiryKindReference: ExpiryKind = ExpiryKind.URL
}
