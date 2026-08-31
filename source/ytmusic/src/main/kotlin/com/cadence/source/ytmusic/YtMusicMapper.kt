package com.cadence.source.ytmusic

import com.cadence.core.model.ArtistRef
import com.cadence.core.model.Artwork
import com.cadence.core.model.ArtworkVariant
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * NewPipe wire types -> domain models. NewPipe/HTTP types NEVER leave this
 * module; only com.cadence.core.model values cross the provider boundary
 * (ADR-0002 / LICENSE_DECISION.md cond.5). Deterministic: given the same
 * extraction output the mapping is stable.
 */
internal object YtMusicMapper {

    val ID: ProviderId = ProviderId("ytmusic")

    private val QUERY_ID = Regex("[?&]v=([0-9A-Za-z_-]{6,})")

    /** Maps a search item to a domain [Track]; returns null when the row must be dropped. */
    fun searchItem(item: InfoItem): Track? {
        if (item.infoType != InfoItem.InfoType.STREAM) return null
        val title = item.name?.takeIf { it.isNotBlank() } ?: return null
        val nativeId = videoIdFromUrl(item.url) ?: return null

        val uploader = (item as? StreamInfoItem)?.uploaderName?.takeIf { it.isNotBlank() }
        val duration = (item as? StreamInfoItem)?.duration?.takeIf { it > 0 }

        val artwork = item.thumbnails
            .mapNotNull(::variant)
            .takeIf { it.isNotEmpty() }
            ?.let(::Artwork)

        return Track(
            ref = TrackRef(ID, nativeId),
            title = title,
            artists = listOfNotNull(uploader?.let(::ArtistRef)),
            durationMs = duration,
            artwork = artwork,
        )
    }

    /** Picks a playable rendition and wraps it with the track's ref. */
    fun resolvedSource(
        ref: TrackRef,
        title: String,
        artist: String?,
        audioStreams: List<AudioStream>,
    ): ResolvedSource {
        val chosen = chooseRendition(audioStreams)
        val uri = chosen?.url?.takeIf { it.isNotBlank() }
            ?: throw SourceError.ResolutionFailed()

        return ResolvedSource(
            ref = ref,
            playbackUri = uri,
            resolutionId = resolutionIdFor(ref.nativeId),
            metadata = sourceMetadata(chosen),
            renditionLabel = chosen?.format?.suffix?.let { "audio/$it" },
        )
    }

    /**
     * Prefer a direct (non-HLS) audio stream; fall back to the last available
     * stream (which may be an HLS manifest) so playback still starts. Deterministic.
     */
    private fun chooseRendition(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val direct = streams.filter { stream ->
            val url = stream.url ?: ""
            !url.contains("/googlevideo/") || !url.contains(".m3u8")
        }
        return (direct.ifEmpty { streams }).lastOrNull()
    }

    private fun sourceMetadata(stream: AudioStream?): SourceMetadata = stream?.let {
        SourceMetadata(
            container = it.format?.suffix,
            codecs = listOfNotNull(it.codec?.takeIf { c -> c.isNotBlank() }),
            bitrateKbps = it.averageBitrate.takeIf { b -> b > 0 }?.div(1000),
            isLive = false,
        )
    } ?: SourceMetadata()

    private fun variant(image: Image): ArtworkVariant? {
        val url = image.url?.takeIf { it.isNotBlank() } ?: return null
        return ArtworkVariant(url = url, heightPx = image.height, widthPx = image.width)
    }

    /** Deterministic opaque resolution id derived from the native id. */
    private fun resolutionIdFor(nativeId: String): String =
        "ytm-" + nativeId.take(12) + "-" + nativeId.length

    internal fun videoIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return QUERY_ID.find(url)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
