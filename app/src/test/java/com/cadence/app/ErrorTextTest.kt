package com.cadence.app

import com.cadence.app.ui.toUserError
import com.cadence.source.api.AuthScope
import com.cadence.source.api.ExpiryKind
import com.cadence.source.api.SourceError
import com.cadence.source.api.UnavailableReason
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Honest states (UXP-9): every typed [SourceError] the real YT Music provider can
 * surface is rendered as friendly user copy and never leaks provider names,
 * exception text, URLs or technical tokens. This pins the UI contract for the
 * provider's failure taxonomy — search and playback surfaces stay honest.
 */
class ErrorTextTest {

    private val leakage = listOf(
        "ytmusic", "newpipe", "exception", "http", "IOException", "stacktrace", ".kts", "java.",
    )

    private fun assertHonest(error: SourceError, expectedRetryable: Boolean) {
        val user = error.toUserError()
        assertTrue(user.message.isNotBlank(), "message must not be blank for $error")
        assertEquals(expectedRetryable, user.retryable, "retryable mismatch for $error")
        val lower = user.message.lowercase()
        for (token in leakage) {
            assertFalse(lower.contains(token), "leak '$token' in message for $error: '${user.message}'")
        }
    }

    @Test
    fun `network and offline states are retryable and honest`() {
        assertHonest(SourceError.NetworkUnreachable(), expectedRetryable = true)
        assertHonest(SourceError.Timeout("search", 5_000), expectedRetryable = true)
        assertHonest(SourceError.RateLimited(30.seconds), expectedRetryable = true)
    }

    @Test
    fun `auth and expiry states are honest`() {
        assertHonest(SourceError.AuthRequired(AuthScope.PLAYBACK), expectedRetryable = false)
        assertHonest(SourceError.AuthRequired(AuthScope.ACCOUNT), expectedRetryable = false)
        assertHonest(SourceError.Expired(ExpiryKind.URL), expectedRetryable = true)
    }

    @Test
    fun `content states are honest`() {
        assertHonest(SourceError.RegionLocked(), expectedRetryable = false)
        assertHonest(SourceError.Unavailable(UnavailableReason.REMOVED), expectedRetryable = false)
        assertHonest(SourceError.Unavailable(UnavailableReason.AGE_RESTRICTED), expectedRetryable = false)
        assertHonest(SourceError.Unsupported("x"), expectedRetryable = false)
    }

    @Test
    fun `transient parse and resolution states are retryable and honest`() {
        assertHonest(SourceError.InvalidResponse("search"), expectedRetryable = true)
        assertHonest(SourceError.ResolutionFailed(), expectedRetryable = true)
    }
}
