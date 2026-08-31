package com.cadence.source.demo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared parser config for demo wire payloads. */
internal val DemoJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Wire DTOs of the synthetic demo backend. These types NEVER leave this module:
 * mappers convert them into com.cadence.core.model values at the boundary
 * (spike spec section 6 / ADR-0002).
 */
@Serializable
data class DemoSearchDoc(
    val results: List<DemoResult> = emptyList(),
    /** Next page number when more pages exist; absent/null on final pages. */
    val next: Int? = null,
)

@Serializable
data class DemoResult(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val year: Int? = null,
    val flags: List<String> = emptyList(),
    val artwork: List<DemoArtwork> = emptyList(),
)

@Serializable
data class DemoArtwork(
    val url: String? = null,
    val w: Int? = null,
    val h: Int? = null,
)

@Serializable
data class DemoResolveDoc(
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("expires_in_seconds") val expiresIn: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    @SerialName("renewal_base64") val renewalBase64: String? = null,
    @SerialName("rendition_label") val renditionLabel: String? = null,
    val metadata: DemoMetadataDto? = null,
)

@Serializable
data class DemoMetadataDto(
    val container: String? = null,
    val codecs: List<String>? = null,
    @SerialName("bitrate_kbps") val bitrateKbps: Int? = null,
    @SerialName("sample_rate_hz") val sampleRateHz: Int? = null,
    @SerialName("loudness_db") val loudnessDb: Double? = null,
    @SerialName("perceptual_loudness_db") val perceptualLoudnessDb: Double? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("is_live") val isLive: Boolean = false,
)
