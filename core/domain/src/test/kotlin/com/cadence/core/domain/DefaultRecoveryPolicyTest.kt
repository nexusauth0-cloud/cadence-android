package com.cadence.core.domain

import com.cadence.source.api.AuthScope
import com.cadence.source.api.ExpiryKind
import com.cadence.source.api.SourceError
import com.cadence.source.api.UnavailableReason
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultRecoveryPolicyTest {

    private val policy = DefaultRecoveryPolicy()

    @Test
    fun `expired retries exactly once`() {
        assertEquals(RecoveryPolicy.Decision.RetryNow::class, policy.decide(SourceError.Expired(ExpiryKind.URL), 1)::class)
        assertEquals(RecoveryPolicy.Decision.GiveUp, policy.decide(SourceError.Expired(ExpiryKind.SESSION), 2))
    }

    @Test
    fun `rate-limited retries only when retryAfter known`() {
        val withDelay = SourceError.RateLimited(30.seconds)
        val blind = SourceError.RateLimited(null)
        assertEquals(RecoveryPolicy.Decision.RetryIn::class, policy.decide(withDelay, 1)::class)
        assertEquals(RecoveryPolicy.Decision.GiveUp, policy.decide(blind, 1))
        assertEquals(RecoveryPolicy.Decision.GiveUp, policy.decide(withDelay, 2))
    }

    @Test
    fun `transient classes get one retry`() {
        listOf(
            SourceError.NetworkUnreachable(),
            SourceError.Timeout("resolve", 1000),
            SourceError.InvalidResponse("ctx"),
            SourceError.ResolutionFailed(),
        ).forEach { err ->
            assertEquals(RecoveryPolicy.Decision.RetryNow, policy.decide(err, 1), err.message)
            assertEquals(RecoveryPolicy.Decision.GiveUp, policy.decide(err, 2), err.message)
        }
    }

    @Test
    fun `permanent classes never retry`() {
        listOf(
            SourceError.AuthRequired(AuthScope.ACCOUNT),
            SourceError.RegionLocked(),
            SourceError.Unavailable(UnavailableReason.PRIVATE),
            SourceError.Unsupported("nope"),
        ).forEach { err ->
            assertEquals(RecoveryPolicy.Decision.GiveUp, policy.decide(err, 1), err.message)
        }
    }
}
