package com.cadence.core.domain

import com.cadence.core.model.OpaquePayload
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.TrackRef
import java.time.Instant

/** Shared deterministic resolution stubs for domain-layer tests. */
object ScriptedResolution {

    fun resolve(ref: TrackRef): ResolvedSource = ResolvedSource(
        ref = ref,
        playbackUri = "https://fixture.demo/stream/${ref.nativeId}?sig=tok",
        resolutionId = "scripted-${ref.nativeId}",
        expiresAt = Instant.ofEpochMilli(1_800_000_000_000),
        renewal = OpaquePayload(ProviderId("demo"), bytes = byteArrayOf(1)),
        metadata = SourceMetadata(bitrateKbps = 128),
        renditionLabel = "scripted",
    )

    fun port(): SourceResolutionPort = object : SourceResolutionPort {
        override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource = resolve(ref)
    }
}
