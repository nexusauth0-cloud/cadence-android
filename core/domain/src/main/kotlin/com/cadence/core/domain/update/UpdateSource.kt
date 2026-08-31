package com.cadence.core.domain.update

/** The currently-known app version at build time. */
const val CURRENT_APP_VERSION: String = "1.0.0"

/** Allow-listed https store host for update links (no arbitrary APK URLs). */
const val UPDATE_STORE_HOST: String = "play.google.com"

/**
 * Port for discovering app updates. The UI / domain never talk to GitHub or the
 * Play Store directly — they depend only on this deterministic abstraction so
 * the state machine is fully testable with a fake source.
 */
interface UpdateSource {
    /**
     * Look for an update newer than [current]. Returns the best applicable
     * update [UpdateInfo], or null when the app is up to date.
     */
    suspend fun check(current: SemanticVersion): UpdateInfo?
}

/**
 * Production-safe default source that reports "no update". Real network update
 * discovery is intentionally NOT wired into the UI layer — the UI/domain depend
 * only on the [UpdateSource] port, so a deterministic source (e.g. a staged
 * release feed) can be injected later without touching the modal/state machine.
 */
object NoopUpdateSource : UpdateSource {
    override suspend fun check(current: SemanticVersion): UpdateInfo? = null
}
