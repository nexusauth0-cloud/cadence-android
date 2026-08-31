package com.cadence.source.api

import kotlin.time.Duration

enum class ExpiryKind { URL, SESSION }

enum class UnavailableReason { REMOVED, PRIVATE, AGE_RESTRICTED, UNKNOWN }

enum class AuthScope { ACCOUNT, PLAYBACK }

/**
 * The ONLY error family providers may throw across the SPI (besides legitimate
 * [kotlinx.coroutines.CancellationException] propagation, which must never be
 * wrapped). Semantics per row are defined by spike spec section 8.
 */
sealed class SourceError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /** No route / DNS / offline. Retry when connectivity returns. */
    class NetworkUnreachable(cause: Throwable? = null) :
        SourceError("network unreachable", cause)

    /** Server too slow. Retryable with backoff; caller-side timeout converts to this at policy level. */
    class Timeout(val operation: String, val elapsedMs: Long, cause: Throwable? = null) :
        SourceError("timeout during $operation after ${elapsedMs}ms", cause)

    /** Provider throttling. Retry honoring [retryAfter]. */
    class RateLimited(val retryAfter: Duration?, cause: Throwable? = null) :
        SourceError("rate limited${retryAfter?.let { ", retry after $it" } ?: ""}", cause)

    /** Valid credentials required; auto-reauth may follow. User-actionable (sign-in prompt). */
    class AuthRequired(val scope: AuthScope) :
        SourceError("authentication required ($scope)")

    /** Resolved artifact expired mid-use. Transparently re-resolvable (ADR-0006 policy). Not user-facing. */
    class Expired(val kind: ExpiryKind) :
        SourceError("resolved artifact expired (${kind})")

    /** Content unavailable in this region. Honest UI message; no retry. */
    class RegionLocked :
        SourceError("content unavailable in region")

    /** Content gone/blocked. No retry except once for UNKNOWN. */
    class Unavailable(val reason: UnavailableReason) :
        SourceError("unavailable (${reason})")

    /** Provider cannot serve this ref/type at all. Never retry. */
    class Unsupported(val detail: String) :
        SourceError("unsupported: $detail")

    /**
     * Wire contract violation (malformed payload). [snippet] MUST already be
     * redacted through com.cadence.core.common.SourceLog and be <=200 chars.
     * Retry once (transient upstream break), then stop.
     */
    class InvalidResponse(val context: String, val snippet: String? = null) :
        SourceError("invalid response during $context${snippet?.let { ": $it" } ?: ""}")

    /** Terminal resolver failure with no more specific classification. Retry once. */
    class ResolutionFailed(cause: Throwable? = null) :
        SourceError("resolution failed", cause)
}