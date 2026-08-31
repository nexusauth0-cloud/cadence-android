package com.cadence.core.domain

import com.cadence.source.api.SourceError
import kotlin.time.Duration

/**
 * Central retry policy (ADR-0006 "recovery policy lives once"). Policies never
 * sleep or poll: they return decisions; callers act on them. `RetryIn` without
 * an active wait surfaces as Failed(retryAfter=…) so UI can schedule.
 */
interface RecoveryPolicy {
    fun decide(error: SourceError, attempt: Int): Decision

    sealed interface Decision {
        data object RetryNow : Decision
        data class RetryIn(val delay: Duration) : Decision
        data object GiveUp : Decision
    }
}

class DefaultRecoveryPolicy(
    private val maxExpiredRetries: Int = 1,
    private val maxRateLimitedRetries: Int = 1,
) : RecoveryPolicy {

    override fun decide(error: SourceError, attempt: Int) : RecoveryPolicy.Decision = when (error) {
        is SourceError.Expired ->
            if (attempt <= maxExpiredRetries) RecoveryPolicy.Decision.RetryNow else RecoveryPolicy.Decision.GiveUp
        is SourceError.RateLimited -> {
            val retry = error.retryAfter
            if (attempt <= maxRateLimitedRetries && retry != null) {
                RecoveryPolicy.Decision.RetryIn(retry)
            } else {
                RecoveryPolicy.Decision.GiveUp
            }
        }
        is SourceError.NetworkUnreachable -> if (attempt == 1) RecoveryPolicy.Decision.RetryNow else RecoveryPolicy.Decision.GiveUp
        is SourceError.Timeout, is SourceError.InvalidResponse, is SourceError.ResolutionFailed ->
            if (attempt == 1) RecoveryPolicy.Decision.RetryNow else RecoveryPolicy.Decision.GiveUp
        is SourceError.AuthRequired,
        is SourceError.RegionLocked,
        is SourceError.Unavailable,
        is SourceError.Unsupported,
        -> RecoveryPolicy.Decision.GiveUp
    }
}
