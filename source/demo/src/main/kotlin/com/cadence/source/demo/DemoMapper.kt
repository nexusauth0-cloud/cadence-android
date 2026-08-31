package com.cadence.source.demo

import com.cadence.core.common.SourceLog
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.Artwork
import com.cadence.core.model.ArtworkVariant
import com.cadence.core.model.OpaquePayload
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.Track
import com.cadence.core.model.TrackFlag
import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import java.time.Clock
import java.time.Instant
import java.util.Base64

/**
 * Wire -> domain mapper for the demo provider. Contract violations become
 * typed [SourceError.InvalidResponse]; droppable search rows (missing
 * identity/title) are skipped rather than fabricated.
 */
object DemoMapper {

    private const val MAX_SNIPPET = 200

    /** Returns null when the row must be dropped (no identity/title). */
    fun track(result: DemoResult): Track? {
        val id = result.id?.takeIf { it.isNotBlank() } ?: return null
        val title = result.title?.takeIf { it.isNotBlank() } ?: return null

        val flags = result.flags.map(::flagOrFail).toSet()

        val artwork = result.artwork
            .mapNotNull { art ->
                art.url?.takeIf { it.isNotBlank() }?.let { ArtworkVariant(it, art.w, art.h) }
            }
            .takeIf { it.isNotEmpty() }
            ?.let(::Artwork)

        return Track(
            ref = TrackRef(DEMO_ID, id),
            title = title,
            artists = listOfNotNull(result.artist?.takeIf { it.isNotBlank() }?.let(::ArtistRef)),
            durationMs = result.durationMs?.takeIf { it >= 0 },
            artwork = artwork,
            flags = flags,
            year = result.year,
        )
    }

    fun resolvedSource(nativeId: String, doc: DemoResolveDoc, clock: Clock): ResolvedSource {
        val url = doc.streamUrl?.takeIf { it.isNotBlank() }
            ?: throw SourceError.InvalidResponse("demo.resolve($nativeId)", "missing stream_url")

        val renewal = doc.renewalBase64?.let { b64 ->
            try {
                OpaquePayload(DEMO_ID, Base64.getDecoder().decode(b64))
            } catch (e: IllegalArgumentException) {
                throw SourceError.InvalidResponse("demo.resolve($nativeId)", "renewal_base64 not valid base64")
            }
        }

        return ResolvedSource(
            ref = TrackRef(DEMO_ID, nativeId),
            playbackUri = url,
            resolutionId = resolutionIdFor(nativeId),
            expiresAt = doc.expiresIn?.let { clock.instant().plusSeconds(it.coerceAtLeast(0)) },
            renewal = renewal,
            httpHeaders = doc.headers,
            metadata = metadataOf(doc.metadata),
            renditionLabel = doc.renditionLabel,
        )
    }

    private fun metadataOf(dto: DemoMetadataDto?): SourceMetadata = SourceMetadata(
        container = dto?.container,
        codecs = dto?.codecs,
        bitrateKbps = dto?.bitrateKbps,
        sampleRateHz = dto?.sampleRateHz,
        loudnessDb = dto?.loudnessDb,
        perceptualLoudnessDb = dto?.perceptualLoudnessDb,
        sizeBytes = dto?.sizeBytes,
        isLive = dto?.isLive ?: false,
    )

    private fun flagOrFail(raw: String): TrackFlag = when (raw) {
        "EXPLICIT" -> TrackFlag.EXPLICIT
        "VIDEO" -> TrackFlag.VIDEO
        else -> throw SourceError.InvalidResponse(
            context = "demo.search",
            // Unknown enum strings are a wire-contract violation; snippet pre-redacted.
            snippet = SourceLog.redactSnippet("unknown flag '$raw'", MAX_SNIPPET),
        )
    }

    private fun resolutionIdFor(nativeId: String): String =
        "demo-" + Integer.toHexString(nativeId.hashCode()) + "-" + nativeId.length

    val DEMO_ID: ProviderId = ProviderId("demo")

    @Suppress("unused")
    private fun nowOf(clock: Clock): Instant = clock.instant()
}
