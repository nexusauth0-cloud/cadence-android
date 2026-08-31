package com.cadence.core.testing

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
import java.time.Instant

/**
 * Canonical golden objects shared between the fake provider and E2E assertions
 * (spike spec section 11). Single definition point keeps goldens honest.
 */
object GoldenFixtures {
    val DEMO = ProviderId("demo")
    val ALPHA_REF = TrackRef(DEMO, "alpha-track")

    const val ALPHA_TITLE = "Alpha Anthem"
    const val ALPHA_ARTIST = "Beta Band"
    const val ALPHA_DURATION_MS = 210_000L
    const val RESOLUTION_TTL_SECONDS = 300L

    fun alphaTrack(): Track = Track(
        ref = ALPHA_REF,
        title = ALPHA_TITLE,
        artists = listOf(ArtistRef(ALPHA_ARTIST)),
        durationMs = ALPHA_DURATION_MS,
        artwork = Artwork(listOf(ArtworkVariant("https://fixture.demo/art/alpha.jpg", widthPx = 640, heightPx = 640))),
        flags = setOf(TrackFlag.EXPLICIT),
        year = 2026,
    )

    fun alphaResolvedSource(clockNow: Instant): ResolvedSource {
        val id = FakeSourceProvider.resolutionIdFor(ALPHA_REF)
        return ResolvedSource(
            ref = ALPHA_REF,
            playbackUri = "https://fixture.demo/stream/alpha-track?sig=tok",
            resolutionId = id,
            expiresAt = clockNow.plusSeconds(RESOLUTION_TTL_SECONDS),
            renewal = OpaquePayload(DEMO, bytes = "renew:alpha-track".toByteArray()),
            httpHeaders = emptyMap(),
            metadata = SourceMetadata(
                container = "mp4",
                codecs = listOf("mp4a.40.2"),
                bitrateKbps = 192,
                sampleRateHz = 44_100,
                loudnessDb = -7.2,
                perceptualLoudnessDb = -6.8,
                sizeBytes = 5_400_000,
            ),
            renditionLabel = "demo-medium",
        )
    }
}
