package com.cadence.core.testing

import com.cadence.core.model.ProviderId
import com.cadence.core.model.QualityPref
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSourceProviderTest {

    private val clock = MutableClock.at("2026-08-26T00:00:00Z")
    private val provider = FakeSourceProvider(clock)

    @Test
    fun `ok scenario resolves with deterministic identity`() = runTest {
        val src = provider.resolve(TrackRef(GoldenFixtures.DEMO, "alpha-track"), ResolutionHints())
        val golden = GoldenFixtures.alphaResolvedSource(clock.instant())
        assertEquals(golden.playbackUri, src.playbackUri)
        assertEquals(golden.resolutionId, src.resolutionId)
        assertEquals(golden.expiresAt, src.expiresAt)
        assertEquals(golden.renewal, src.renewal)
        assertEquals(golden.metadata, src.metadata)
        assertEquals(golden.renditionLabel, src.renditionLabel)
    }

    @Test
    fun `unavailable scenario throws typed Unavailable`() = runTest {
        val err = assertFailsWith<SourceError> {
            provider.resolve(TrackRef(GoldenFixtures.DEMO, "unavailable/x"), ResolutionHints())
        }
        assertIs<SourceError.Unavailable>(err)
    }

    @Test
    fun `rate-limited scenario honors retryAfter then succeeds`() = runTest {
        val ref = TrackRef(GoldenFixtures.DEMO, "rate-limited/rl1")
        val first = assertFailsWith<SourceError> { provider.resolve(ref, ResolutionHints()) }
        val rateLimited = assertIs<SourceError.RateLimited>(first)
        assertNotNull(rateLimited.retryAfter)

        // Second call succeeds (deterministic one-shot limit), expiry uses injected clock.
        val second = provider.resolve(ref, ResolutionHints())
        assertEquals(clock.instant().plusSeconds(FakeSourceProvider.TTL_SECONDS), second.expiresAt)
    }

    @Test
    fun `auth-failed scenario gates on authState`() = runTest {
        val ref = TrackRef(GoldenFixtures.DEMO, "auth-failed/gated")
        assertFailsWith<SourceError.AuthRequired> { provider.resolve(ref, ResolutionHints()) }
        provider.login("tester")
        val src = provider.resolve(ref, ResolutionHints())
        assertEquals("https://fixture.demo/stream/gated?sig=tok", src.playbackUri)
    }

    @Test
    fun `malformed scenario yields InvalidResponse`() = runTest {
        val err = assertFailsWith<SourceError> {
            provider.resolve(TrackRef(GoldenFixtures.DEMO, "malformed/m"), ResolutionHints())
        }
        assertIs<SourceError.InvalidResponse>(err)
    }

    @Test
    fun `cancelled-immediately preserves cancellation identity`() = runTest {
        val job = async {
            provider.resolve(TrackRef(GoldenFixtures.DEMO, "cancelled-immediately/x"), ResolutionHints())
        }
        runCurrent()
        job.cancel()
        val err = assertFailsWith<CancellationException> { job.await() }
        // Regression proof: cancellation is NOT converted into SourceError.
        assertTrue(err !is SourceError, "cancellation must never surface as SourceError")
    }

    @Test
    fun `delayed scenario respects virtual time and hints echo`() = runTest {
        val hints = ResolutionHints(qualityPref = QualityPref.HIGH)
        val job = async {
            provider.resolve(TrackRef(GoldenFixtures.DEMO, "delayed/500-hello"), hints)
        }
        runCurrent()
        assertEquals(1, provider.activeWorkCount(), "work must be in flight before virtual time advances")
        advanceTimeBy(499)
        runCurrent()
        assertTrue(job.isActive)
        advanceTimeBy(1)
        runCurrent()
        val src = job.await()
        assertEquals("hello", src.ref.nativeId)
        assertEquals("demo-medium-high", src.renditionLabel)
        assertSameHints(hints, provider.hintsSentFor("delayed/500-hello"))
        assertEquals(0, provider.activeWorkCount(), "no leaked work after completion")
        job.cancelAndJoin()
    }

    @Test
    fun `unknown scenario keys are Unsupported not silent`() = runTest {
        assertFailsWith<SourceError.Unsupported> {
            provider.resolve(TrackRef(ProviderId("demo"), "mystery/key"), ResolutionHints())
        }
    }

    private fun assertSameHints(expected: ResolutionHints, actual: ResolutionHints?) {
        assertNotNull(actual)
        assertEquals(expected.qualityPref, actual.qualityPref)
        assertEquals(expected.offlineOnly, actual.offlineOnly)
    }
}
