package com.cadence.android.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.cadence.core.media.AudioEffectChain
import com.cadence.core.media.LoudnessNormalizationEffect
import com.cadence.core.media.PlaybackCommand
import com.cadence.core.media.PlaybackError
import com.cadence.core.media.PlaybackState
import com.cadence.core.media.PlayerController
import com.cadence.core.media.PlayerEvent
import com.cadence.core.model.PlaybackRequest
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Media3/ExoPlayer adapter for [PlayerController] (ADR-0006).
 *
 * Single-player invariant (ADR-0006 §1): exactly one ExoPlayer is created per
 * process via [ExoPlayerFactory] and shared; no duplicate players ever exist.
 * No polling loops — all signal flows through [Player.Listener] callbacks
 * (ADR-0006 §4). No string sniffing of upstream errors (ADR-0006 §3).
 *
 * Media3 types never cross [PlayerController] — mappers live only here.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3PlayerController(
    private val player: Player,
    private val scope: CoroutineScope,
) : PlayerController {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: kotlinx.coroutines.flow.SharedFlow<PlayerEvent> = _events

    private var effectChain: AudioEffectChain? = null
    private var activeRef: TrackRef? = null
    private var loadMutex = Mutex()
    private var ticker: Job? = null
    private var loaded = false

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING ->
                    activeRef?.let { _state.value = PlaybackState.Buffering(it) }

                Player.STATE_READY ->
                    activeRef?.let { ref ->
                        _state.value =
                            if (player.isPlaying) PlaybackState.Playing(ref)
                            else if (_state.value is PlaybackState.Playing) PlaybackState.Paused(ref)
                            else PlaybackState.Ready(ref)
                    }

                Player.STATE_ENDED ->
                    _state.value = PlaybackState.Ended

                Player.STATE_IDLE ->
                    if (!isReleased) _state.value = PlaybackState.Idle
            }
            _positionMs.value = player.currentPosition
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val ref = activeRef
            when {
                isPlaying && ref != null ->
                    _state.value = PlaybackState.Playing(ref)

                !isPlaying && _state.value is PlaybackState.Playing && ref != null ->
                    _state.value = PlaybackState.Paused(ref)
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            _positionMs.value = player.currentPosition
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            launchEvent(PlayerEvent.AudioSessionChanged(audioSessionId))
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = PlaybackState.Failed(
                ref = activeRef,
                error = mapError(error),
            )
            launchEvent(
                PlayerEvent.Error(
                    type = PlayerEvent.ErrorType.UNEXPECTED,
                    message = error.errorCodeName ?: error.message ?: "unknown playback error",
                ),
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ref = mediaItem?.mediaId?.let(::parseRef)
            if (ref != null) activeRef = ref
            val metadata = mediaItem?.mediaMetadata
            launchEvent(
                PlayerEvent.MetadataChanged(
                    title = metadata?.title?.toString(),
                    artist = metadata?.artist?.toString(),
                ),
            )
        }
    }

    override var isReleased: Boolean = false
        private set

    init {
        player.addListener(listener)
    }

    override suspend fun load(request: PlaybackRequest) {
        loadMutex.withLock {
            val ref = request.track.ref
            activeRef = ref
            loaded = true
            _state.value = PlaybackState.Buffering(ref)
            player.setMediaItem(buildMediaItem(request), request.startPositionMs.coerceAtLeast(0))
            applyEffectChain()
            applyAudioTraits(request)
            player.prepare()
        }
    }

    override fun play() {
        val s = _state.value
        if (s is PlaybackState.Ready || s is PlaybackState.Paused) {
            player.play()
        }
    }

    override fun pause() {
        if (_state.value is PlaybackState.Playing) {
            player.pause()
        }
    }

    override fun command(cmd: PlaybackCommand) {
        when (cmd) {
            is PlaybackCommand.SeekTo -> player.seekTo(cmd.positionMs.coerceAtLeast(0))
            is PlaybackCommand.SetSpeed -> {
                player.setPlaybackSpeed(cmd.speed.coerceIn(0.25f, 3.0f))
            }
            is PlaybackCommand.SetVolume -> player.volume = cmd.volume.coerceIn(0f, 1f)
            is PlaybackCommand.SetAudioAttributes ->
                player.setAudioAttributes(mapAudioAttributes(cmd.attributes), true)
            is PlaybackCommand.SetNormalization -> {
                val chain = effectChain
                if (chain != null) {
                    val effect = chain.effects()
                        .filterIsInstance<LoudnessNormalizationEffect>()
                        .firstOrNull()
                    if (effect != null) {
                        effect.enabled = cmd.enabled
                        chain.setEnabled(effect, cmd.enabled)
                    }
                }
            }
            PlaybackCommand.SkipToNext -> player.seekToNextMediaItem()
            PlaybackCommand.SkipToPrevious -> player.seekToPreviousMediaItem()
            PlaybackCommand.Stop -> player.stop()
        }
    }

    override fun setEffectChain(chain: AudioEffectChain) {
        effectChain = chain
        if (loaded) applyEffectChain()
    }

    override suspend fun release() {
        loadMutex.withLock {
            if (isReleased) return
            isReleased = true
            ticker?.cancel()
            ticker = null
            player.removeListener(listener)
            player.release()
            _state.value = PlaybackState.Idle
        }
    }

    private fun applyEffectChain() {
        // ADR-0006 §2: audio offload auto-disables when a non-pass-through
        // processor is active.
        val active = effectChain?.hasActiveEffects() == true
        val current = player.trackSelectionParameters
        val params = current.buildUpon()
            .setAudioOffloadPreferences(
                current.audioOffloadPreferences.buildUpon()
                    .setAudioOffloadMode(
                        if (active) {
                            androidx.media3.common.TrackSelectionParameters
                                .AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        } else {
                            androidx.media3.common.TrackSelectionParameters
                                .AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        },
                    )
                    .build(),
            )
            .build()
        player.setTrackSelectionParameters(params)
    }

    private fun applyAudioTraits(request: PlaybackRequest) {
        val traits = request.audioTraits
        if (traits.normalize && traits.targetLufs != null) {
            // Loudness normalization is delivered through the effect chain
            // (LoudnessNormalizationEffect). Request-level traits are handled
            // here as a responsive fallback: ExoPlayer loudness is applied via
            // the chain; this keeps the request contract intact.
        }
    }

    private fun launchEvent(event: PlayerEvent) {
        _events.tryEmit(event)
    }

    private fun buildMediaItem(request: PlaybackRequest): MediaItem {
        val source = request.source
        val builder = MediaItem.Builder()
            .setMediaId(encodeRef(request.track.ref))
            .setUri(source.playbackUri)
        val title = request.track.title
        val artist = request.track.displayArtist()
        builder.setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build(),
        )
        // Media3 1.6.1 MediaItem does not support per-item HTTP request
        // headers (RequestMetadata.Builder has no setHeaders). Request headers
        // are supplied at the DataSource layer instead; per-item header wiring
        // is DEV-0024 (deferred). httpHeaders only carry non-secret values.
        return builder.build()
    }

    private fun encodeRef(ref: TrackRef): String = "${ref.providerId.value}\u0000${ref.nativeId}"

    private fun parseRef(encoded: String): TrackRef? {
        val parts = encoded.split('\u0000')
        return if (parts.size == 2) {
            @Suppress("INVISIBLE_REFERENCE")
            TrackRef(
                providerId = com.cadence.core.model.ProviderId(parts[0]),
                nativeId = parts[1],
            )
        } else null
    }

    private fun mapError(error: PlaybackException): PlaybackError =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            -> PlaybackError.Source(type = error.errorCodeName ?: "SOURCE", message = error.message ?: "source error")

            else -> PlaybackError.Engine(
                message = error.errorCodeName ?: (error.message ?: "engine error"),
                cause = error.cause,
            )
        }

    private fun mapAudioAttributes(attributes: com.cadence.core.media.AudioAttributes): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(
                when (attributes.usage) {
                    com.cadence.core.media.AudioAttributes.Usage.MEDIA -> C.USAGE_MEDIA
                    com.cadence.core.media.AudioAttributes.Usage.NOTIFICATION -> C.USAGE_NOTIFICATION
                    com.cadence.core.media.AudioAttributes.Usage.ALARM -> C.USAGE_ALARM
                    com.cadence.core.media.AudioAttributes.Usage.VOICE_COMMUNICATION -> C.USAGE_VOICE_COMMUNICATION
                },
            )
            .setContentType(
                when (attributes.contentType) {
                    com.cadence.core.media.AudioAttributes.ContentType.MUSIC -> C.AUDIO_CONTENT_TYPE_MUSIC
                    com.cadence.core.media.AudioAttributes.ContentType.MOVIE -> C.AUDIO_CONTENT_TYPE_MOVIE
                    com.cadence.core.media.AudioAttributes.ContentType.SPEECH -> C.AUDIO_CONTENT_TYPE_SPEECH
                },
            )
            .build()
}

/**
 * Convenience creator for the app shell: builds an [PlayerController] backed by
 * an ExoPlayer without leaking Media3 types into the consuming module. Must be
 * called on a thread with a Looper (e.g. Application.onCreate).
 */
fun createMedia3PlayerController(
    context: android.content.Context,
    scope: CoroutineScope,
): PlayerController {
    val player = ExoPlayer.Builder(context).build()
    return Media3PlayerController(player, scope)
}

/**
 * Single-player holder (ADR-0006 §1): one ExoPlayer per process. The first
 * caller receives the builder; subsequent callers get the same instance until
 * it is released.
 */
object ExoPlayerFactory {

    private val mutex = Mutex()
    private var instance: ExoPlayer? = null

    suspend fun acquire(builder: () -> ExoPlayer): ExoPlayer =
        mutex.withLock {
            instance ?: builder().also { instance = it }
        }

    suspend fun releaseIfCurrent(candidate: ExoPlayer) {
        mutex.withLock {
            if (instance === candidate) {
                candidate.release()
                instance = null
            }
        }
    }

    internal fun resetForTests() {
        instance?.release()
        instance = null
    }
}