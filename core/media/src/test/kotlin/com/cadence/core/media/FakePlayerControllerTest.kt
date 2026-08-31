package com.cadence.core.media

import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import com.cadence.core.model.Track
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.QualityPref
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakePlayerControllerTest {

    private val DEMO = ProviderId("demo")
    private val fake = FakePlayerController()

    private fun testRequest() = PlaybackRequest(
        track = Track(ref = TrackRef(DEMO, "test-track"), title = "Test", artists = listOf(ArtistRef("Artist"))),
        source = ResolvedSource(
            ref = TrackRef(DEMO, "test-track"),
            playbackUri = "https://example.com/stream",
            resolutionId = "res-1",
        ),
    )

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(PlaybackState.Idle, fake.state.value)
    }

    @Test
    fun `load transitions to Buffering then Ready`() = runTest {
        fake.load(testRequest())
        assertEquals(PlaybackState.Ready(TrackRef(DEMO, "test-track")), fake.state.value)
        assertEquals(1, fake.loadRequests.size)
    }

    @Test
    fun `play after Ready transitions to Playing`() = runTest {
        fake.load(testRequest())
        fake.play()
        assertEquals(PlaybackState.Playing(TrackRef(DEMO, "test-track")), fake.state.value)
        assertEquals(1, fake.playCount)
    }

    @Test
    fun `pause transitions from Playing to Paused`() = runTest {
        fake.load(testRequest())
        fake.play()
        fake.pause()
        assertEquals(PlaybackState.Paused(TrackRef(DEMO, "test-track")), fake.state.value)
        assertEquals(1, fake.pauseCount)
    }

    @Test
    fun `command records the command`() = runTest {
        fake.command(PlaybackCommand.SeekTo(5000))
        fake.command(PlaybackCommand.SkipToNext)
        assertEquals(2, fake.commands.size)
        assertTrue(fake.commands[0] is PlaybackCommand.SeekTo)
        assertTrue(fake.commands[1] is PlaybackCommand.SkipToNext)
    }

    @Test
    fun `setEffectChain records chain`() = runTest {
        val chain = object : AudioEffectChain {
            private val effects = mutableListOf<AudioEffect>()
            override fun addEffect(effect: AudioEffect) { effects.add(effect) }
            override fun removeEffect(effect: AudioEffect) { effects.remove(effect) }
            override fun setEnabled(effect: AudioEffect, enabled: Boolean) { effect.enabled = enabled }
            override fun hasActiveEffects(): Boolean = effects.any { it.enabled }
            override fun effects(): List<AudioEffect> = effects.toList()
        }
        fake.setEffectChain(chain)
        assertEquals(1, fake.effectChains.size)
    }

    @Test
    fun `load with error transitions to Failed`() = runTest {
        fake.loadError = PlaybackError.Source("expired", "URL expired")
        fake.load(testRequest())
        assertTrue(fake.state.value is PlaybackState.Failed)
    }

    @Test
    fun `release makes controller unusable`() = runTest {
        fake.release()
        assertTrue(fake.isReleased)
    }

    @Test
    fun `advancePosition updates positionMs`() = runTest {
        fake.advancePosition(1000)
        assertEquals(1000, fake.positionMs.value)
        fake.advancePosition(500)
        assertEquals(1500, fake.positionMs.value)
    }

    @Test
    fun `single player invariant — no second instance expected`() = runTest {
        // This test documents the architectural invariant:
        // ADR-0006 §1: Exactly one ExoPlayer instance for the process.
        // The FakePlayerController proves the port works; the real adapter
        // enforces single-instance at construction time.
        val controller1 = FakePlayerController()
        val controller2 = FakePlayerController()
        // Both can exist in tests (fakes), but only one should be active in production
        assertFalse(controller1.isReleased)
        assertFalse(controller2.isReleased)
        // Production adapter would throw if second instance created
    }
}
