package com.cadence.core.model

/** Quality preference hint passed to resolution. Providers map it to concrete renditions. */
enum class QualityPref { AUTO, LOW, HIGH }

data class ResolutionHints(
    val qualityPref: QualityPref = QualityPref.AUTO,
    val offlineOnly: Boolean = false,
)

/**
 * Descriptive technical facts about a resolved rendition. All fields optional:
 * a fully-absent metadata block must remain playable (normalization simply skips).
 */
data class SourceMetadata(
    val container: String? = null,
    val codecs: List<String>? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    val sizeBytes: Long? = null,
    val isLive: Boolean = false,
)

/**
 * Provider-scoped opaque blob used to renew/re-authenticate a resolved source
 * (ADR-0002 "renewal"). Only the matching [providerId] may decode the bytes;
 * every other layer treats this as an opaque byte array. Never logged.
 */
class OpaquePayload(
    val providerId: ProviderId,
    val bytes: ByteArray,
) {
    private val hash = bytes.contentHashCode()

    override fun equals(other: Any?): Boolean =
        other is OpaquePayload && other.providerId == providerId && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * providerId.hashCode() + hash
    override fun toString(): String = "OpaquePayload(providerId=$providerId, ${bytes.size} bytes)"
}

/**
 * Everything a player needs to start playback for one rendition — the terminal
 * output of RESOLVE (spike spec section 4.10, renaming ADR-0002's provisional
 * name `StreamManifest` with unchanged semantics).
 *
 * Exactly one primary rendition per instance; quality switching later is a
 * re-resolve, not a field mutation. If [expiresAt] is set, either [renewal]
 * exists or the recovery policy will transparently re-resolve on expiry.
 *
 * [httpHeaders] carries only non-secret request headers (ADR-0004 / spike 15):
 * values must never contain credentials and are never logged.
 */
class ResolvedSource(
    val ref: TrackRef,
    val playbackUri: String,
    val resolutionId: String,
    val expiresAt: java.time.Instant? = null,
    val renewal: OpaquePayload? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val metadata: SourceMetadata = SourceMetadata(),
    val renditionLabel: String? = null,
) {
    init {
        require(playbackUri.isNotBlank()) { "playbackUri must not be blank" }
        require(resolutionId.isNotBlank()) { "resolutionId must not be blank" }
        require(httpHeaders.values.none { it.isBlank() }) { "blank header values are forbidden" }
    }

    fun isExpiredAt(now: java.time.Instant): Boolean = expiresAt != null && !now.isBefore(expiresAt)

    override fun toString(): String =
        "ResolvedSource(resolutionId=$resolutionId, ref=$ref, uri=${redactedUri()}, expiresAt=$expiresAt)"

    /** URI with any query component stripped — safe for logs. */
    fun redactedUri(): String = playbackUri.substringBefore('?')
}
