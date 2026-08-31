package com.cadence.android.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackSelectionParameters
import com.cadence.core.media.AudioEffectChain
import com.cadence.core.media.PlaybackState
import com.cadence.core.model.AudioTraits
import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the Media3 adapter maps domain [PlaybackRequest]s to the player and
 * reflects deterministic state in the domain [PlaybackState] flow (ADR-0006),
 * while Media3 types never cross the [com.cadence.core.media.PlayerController]
 * boundary — the adapter is the only place that touches Media3.
 *
 * The fake is a controllable [SimpleBasePlayer]; listener callbacks are driven
 * synchronously through `invalidateState`.
 */
@RunWith(RobolectricTestRunner::class)
class Media3PlayerControllerTest {

    private lateinit var player: ControllablePlayer
    private lateinit var scope: TestScope
    private lateinit var controller: Media3PlayerController

    private fun scopeRef(nativeId: String = "n1") =
        TrackRef(providerId = ProviderId("demo"), nativeId = nativeId)

    private fun request(nativeId: String = "n1") = PlaybackRequest(
        track = Track(ref = scopeRef(nativeId), title = "Title"),
        source = ResolvedSource(
            ref = scopeRef(nativeId),
            playbackUri = "https://example.com/stream.mp3",
            resolutionId = "res-1",
        ),
        startPositionMs = 0,
        audioTraits = AudioTraits(),
    )

    @Before
    fun setUp() {
        player = ControllablePlayer()
        scope = TestScope()
        controller = Media3PlayerController(player, scope)
    }

    @Test
    fun loadCarriesRefAndPlaybackUriToMedia3() = runTest {
        controller.load(request())
        val state = controller.state.value
        assertTrue("expected active state, was $state", state !is PlaybackState.Idle)
        val ref = when (state) {
            is PlaybackState.Buffering -> state.ref
            is PlaybackState.Ready -> state.ref
            is PlaybackState.Playing -> state.ref
            is PlaybackState.Paused -> state.ref
            else -> null
        }
        assertEquals(scopeRef(), ref)
        assertEquals(
            "https://example.com/stream.mp3",
            player.latestMediaItem!!.localConfiguration!!.uri.toString(),
        )
    }

    @Test
    fun noActiveEffectsLeavesOffloadEnabledOnLoad() = runTest {
        controller.load(request())
        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            player.trackSelectionParameters().audioOffloadPreferences.audioOffloadMode,
        )
    }

    @Test
    fun activeEffectProcessorDisablesOffload() = runTest {
        val chain = object : AudioEffectChain {
            override fun addEffect(effect: com.cadence.core.media.AudioEffect) {}
            override fun removeEffect(effect: com.cadence.core.media.AudioEffect) {}
            override fun setEnabled(effect: com.cadence.core.media.AudioEffect, enabled: Boolean) {}
            override fun hasActiveEffects(): Boolean = true
            override fun effects(): List<com.cadence.core.media.AudioEffect> = emptyList()
        }
        controller.load(request())
        controller.setEffectChain(chain)
        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
            player.trackSelectionParameters().audioOffloadPreferences.audioOffloadMode,
        )
    }

    @Test
    fun releaseIsIdempotentAndFlipsReleased() = runTest {
        controller.load(request())
        controller.release()
        assertEquals(PlaybackState.Idle, controller.state.value)
        assertTrue(controller.isReleased)
        assertTrue(player.released)
        controller.release() // safe no-op
        assertTrue(controller.isReleased)
    }
}

/**
 * Minimal controllable [SimpleBasePlayer]: accepts listeners, records the
 * latest media item and track-selection mutations, and lets tests read state.
 */
private class ControllablePlayer(
    looper: android.os.Looper = android.os.Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    var latestMediaItem: MediaItem? = null
        private set
    var released: Boolean = false
        private set

    private var offloadMode: Int =
        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED

    private var model: State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(Player.STATE_IDLE)
        .setPlaylist(emptyList())
        .setTrackSelectionParameters(
            TrackSelectionParameters.DEFAULT.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.DEFAULT.audioOffloadPreferences.buildUpon()
                        .setAudioOffloadMode(offloadMode)
                        .build(),
                )
                .build(),
        )
        .build()

    override fun getState(): State = model

    fun trackSelectionParameters(): TrackSelectionParameters = model.trackSelectionParameters

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): com.google.common.util.concurrent.ListenableFuture<*> {
        latestMediaItem = mediaItems.firstOrNull()
        val playlist = mediaItems.map {
            androidx.media3.common.SimpleBasePlayer.MediaItemData.Builder(it.mediaId)
                .setMediaItem(it)
                .build()
        }
        model = model.buildUpon()
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(startIndex)
            .setContentPositionMs(startPositionMs)
            .build()
        return com.google.common.util.concurrent.Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): com.google.common.util.concurrent.ListenableFuture<*> {
        model = model.buildUpon()
            .setPlaybackState(Player.STATE_READY)
            .build()
        return com.google.common.util.concurrent.Futures.immediateVoidFuture()
    }

    override fun handleSetTrackSelectionParameters(
        trackSelectionParameters: TrackSelectionParameters,
    ): com.google.common.util.concurrent.ListenableFuture<*> {
        offloadMode = trackSelectionParameters.audioOffloadPreferences.audioOffloadMode
        model = model.buildUpon()
            .setTrackSelectionParameters(trackSelectionParameters)
            .build()
        return com.google.common.util.concurrent.Futures.immediateVoidFuture()
    }

    override fun handleRelease(): com.google.common.util.concurrent.ListenableFuture<*> {
        released = true
        return com.google.common.util.concurrent.Futures.immediateVoidFuture()
    }
}
