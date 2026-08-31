package com.cadence.core.domain.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic, event-driven app-update state machine (no polling loops,
 * no watchdog timers). Owns the single source of truth for the update modal
 * UX and encodes the Phase 5D policy:
 *
 *  - only updates on the user's [channel] are considered;
 *  - a discovered update triggers `Available`; the UI shows a modal;
 *  - non-critical updates may be dismissed/deferred (the modal is hidden again);
 *  - critical updates CANNOT be dismissed — re-opening the app re-shows them;
 *  - store links are validated against [UPDATE_STORE_HOST] over https (no
 *    arbitrary APK URLs), so an untrusted [UpdateSource] cannot drive a
 *    dangerous deep link.
 */
class UpdateManager(
    private val source: UpdateSource,
    private val currentVersion: SemanticVersion = SemanticVersion.parse(CURRENT_APP_VERSION)
        ?: SemanticVersion(1, 0, 0),
    val channel: UpdateChannel = UpdateChannel.STABLE,
    initState: UpdateState = UpdateState.Hidden,
) {

    private val _state = MutableStateFlow(initState)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Run an update check. Transition: Hidden/NotAvailable/Failed/Available ->
     * Checking -> outcome. Called explicitly by the UI (e.g. once at startup);
     * there is no timer, poller or watchdog.
     */
    suspend fun check() {
        _state.value = UpdateState.Checking
        val info = runCatching { source.check(currentVersion) }
        _state.update {
            when {
                info.isFailure -> UpdateState.Failed(info.exceptionOrNull()?.message)
                else -> {
                    val candidate = info.getOrNull()
                    val applicable = candidate != null &&
                        candidate.version > currentVersion &&
                        candidate.channel == channel &&
                        isSafeStoreUrl(candidate.storeUrl)
                    if (applicable) UpdateState.Available(candidate) else UpdateState.NotAvailable
                }
            }
        }
    }

    /** Dismiss (defer) the current update; critical updates cannot be deferred. */
    fun dismiss() {
        val current = _state.value as? UpdateState.Available ?: return
        if (UpdatePolicy.canDefer(current.info)) _state.value = UpdateState.Hidden
    }

    companion object {
        fun isSafeStoreUrl(url: String): Boolean {
            return runCatching {
                val u = java.net.URI(url)
                u.scheme == "https" && (u.host == UPDATE_STORE_HOST || u.host?.endsWith(".$UPDATE_STORE_HOST") == true)
            }.getOrDefault(false)
        }
    }
}

/** Update policy lookups, kept pure for direct testing. */
object UpdatePolicy {
    /** A critical update must always be resolved — it cannot be permanently dismissed. */
    fun canDefer(info: UpdateInfo): Boolean = !info.critical
}
