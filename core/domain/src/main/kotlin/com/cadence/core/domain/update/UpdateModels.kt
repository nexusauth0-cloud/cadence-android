package com.cadence.core.domain.update

/** Distribution channel an update is published on. */
enum class UpdateChannel { STABLE, BETA }

/**
 * A discovered app update. Pure domain value — never a raw HTTP/network type.
 * [storeUrl] is the store/app page the user is sent to; it is validated by the
 * manager against an allow-listed https store host (no arbitrary APK URLs).
 */
data class UpdateInfo(
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val releaseNotes: String,
    /** Oldest installable version; below this the update is effectively required. */
    val minSupported: SemanticVersion,
    val critical: Boolean,
    val linkLabel: String = "Get the update",
    val storeUrl: String,
)

/** The observable update UX state (no polling — transitions are event-driven). */
sealed interface UpdateState {
    /** Nothing to show now (start / dismissed / not applicable). */
    data object Hidden : UpdateState

    /** A check is in flight. */
    data object Checking : UpdateState

    data class Available(val info: UpdateInfo) : UpdateState

    data object NotAvailable : UpdateState

    data class Failed(val message: String?) : UpdateState
}
