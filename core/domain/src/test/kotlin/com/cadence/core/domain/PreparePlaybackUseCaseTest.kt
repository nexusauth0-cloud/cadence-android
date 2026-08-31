package com.cadence.core.domain

import com.cadence.core.model.Track
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Policy + outcome semantics of PreparePlaybackUseCase against scripted ports.
 * Uses a local engine stub because :core:testing cannot be a dependency here
 * (it itself depends on :core:domain — no cycles allowed).
 */
class PreparePlaybackUseCaseTest {

    private class CountingEngine : PlaybackEngine {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val positionMs = MutableStateFlow(0L)
        val loads = Collections.synchronizedList(mutableListOf<com.cadence.core.model.PlaybackRequest>())
        override suspend fun load(request: com.cadence.core.model.PlaybackRequest) { loads.add(request) }
        override fun play() {}
        override fun pause() {}
        override suspend fun release() {}
    }

    @Test
    fun `expired error transparently retries once then succeeds`() = runTest {
        val engine = CountingEngine()
        var decisions = 0
        val useCase = PreparePlaybackUseCase(
            resolution = FlakyResolution(firstError = SourceError.Expired(com.cadence.source.api.ExpiryKind.URL)),
            engine = engine,
            policy = RecordingPolicy { err, attempt ->
                decisions++
                assertIs<SourceError.Expired>(err)
                DefaultRecoveryPolicy().decide(err, attempt)
            },
        )
        val outcome = useCase(track("retry-me"))
        assertIs<PlaybackOutcome.Started>(outcome)
        assertEquals(1, engine.loads.size, "second attempt reaches engine")
        assertEquals(1, decisions, "policy consulted once; RetryNow re-resolve succeeds silently")
    }

    @Test
    fun `rate-limited surfaces Failed with retryAfter and never sleeps`() = runTest {
        val engine = CountingEngine()
        val useCase = PreparePlaybackUseCase(
            resolution = FlakyResolution(SourceError.RateLimited(90.seconds)),
            engine = engine,
        )
        val failed = assertIs<PlaybackOutcome.Failed>(useCase(track("rl")))
        assertEquals(90_000L, failed.retryAfter?.inWholeMilliseconds)
        assertEquals(0, engine.loads.size, "no load on failure path")
    }

    @Test
    fun `give-up errors do not retry`() = runTest {
        val engine = CountingEngine()
        var decisions = 0
        val useCase = PreparePlaybackUseCase(
            resolution = FlakyResolution(SourceError.Unavailable(com.cadence.source.api.UnavailableReason.REMOVED)),
            engine = engine,
            policy = RecordingPolicy { _, _ -> decisions++; RecoveryPolicy.Decision.GiveUp },
        )
        assertIs<PlaybackOutcome.Failed>(useCase(track("gone")))
        assertEquals(1, decisions)
    }

    @Test
    fun `non-SourceError throwables propagate raw - no catch-all conversion`() = runTest {
        val boom = IllegalStateException("boom")
        val useCase = PreparePlaybackUseCase(
            resolution = FlakyResolution(rawThrowable = boom),
            engine = CountingEngine(),
        )
        assertEquals(boom, assertFailsWith<IllegalStateException> { useCase(track("raw")) })
    }

    private fun track(nativeId: String) =
        Track(ref = TrackRef(ProviderId("demo"), nativeId), title = "stub-$nativeId")
}

private class RecordingPolicy(
    private val impl: (SourceError, Int) -> RecoveryPolicy.Decision,
) : RecoveryPolicy {
    override fun decide(error: SourceError, attempt: Int): RecoveryPolicy.Decision = impl(error, attempt)
}

private class FlakyResolution(
    private val firstError: SourceError? = null,
    private val rawThrowable: Throwable? = null,
) : SourceResolutionPort {
    private var called = false

    override suspend fun resolve(ref: TrackRef, hints: com.cadence.core.model.ResolutionHints): com.cadence.core.model.ResolvedSource {
        if (rawThrowable != null) throw rawThrowable
        firstError?.let { err ->
            if (!called) {
                called = true
                throw err
            }
        }
        return ScriptedResolution.resolve(ref)
    }
}
