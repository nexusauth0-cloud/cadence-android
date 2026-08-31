package com.cadence.app.ui

import com.cadence.core.model.Track
import com.cadence.source.api.SourceError

/**
 * Typed error matrix (§13 of PRODUCT-ARCHITECTURE): maps domain error types to
 * user-facing copy + whether a retry is meaningful. The UI never renders
 * exception text, provider names, URLs, or stack traces (UXP-9).
 */
data class UserError(
    val message: String,
    val retryable: Boolean,
)

fun SourceError.toUserError(): UserError = when (this) {
    is SourceError.NetworkUnreachable -> UserError("You're offline.", retryable = true)
    is SourceError.Timeout -> UserError("Taking too long.", retryable = true)
    is SourceError.RateLimited -> UserError("Paused for a moment — try again shortly.", retryable = true)
    is SourceError.AuthRequired -> UserError("Sign-in is needed to play this.", retryable = false)
    is SourceError.Expired -> UserError("That copy expired — refreshing.", retryable = true)
    is SourceError.RegionLocked -> UserError("Not available in your region.", retryable = false)
    is SourceError.Unavailable -> UserError("This song isn't available.", retryable = false)
    is SourceError.Unsupported -> UserError("Not supported here.", retryable = false)
    is SourceError.InvalidResponse -> UserError("Search is acting up — try again.", retryable = true)
    is SourceError.ResolutionFailed -> UserError("Couldn't load that song.", retryable = true)
}

/** Human summary for a track row / mini-player from domain data only. */
fun Track.durationLabel(): String {
    val ms = durationMs ?: return ""
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
