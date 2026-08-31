package com.cadence.core.testing

import com.cadence.core.domain.update.SemanticVersion
import com.cadence.core.domain.update.UpdateChannel
import com.cadence.core.domain.update.UpdateInfo
import com.cadence.core.domain.update.UpdateSource

/**
 * Deterministic [UpdateSource] for the update state-machine tests and UI
 * previews. Configurable per-version (including a critical variant), with a
 * failure flag to exercise the `Failed` state and a default that is "no
 * update" so UI test setups start from a clean, non-intrusive state.
 */
class FakeUpdateSource : UpdateSource {

    var versions: Map<String, UpdateInfo> = emptyMap()
        private set
    var enqueueFailure: IllegalStateException? = null
    var callsCompleted: Int = 0

    fun returnUpdate(
        version: String,
        channel: UpdateChannel = UpdateChannel.STABLE,
        critical: Boolean = false,
        minSupported: String = "1.0.0",
        linkLabel: String = "Get the update",
    ) {
        val v = checkNotNull(SemanticVersion.parse(version))
        versions = versions + (version to UpdateInfo(
            version = v,
            channel = channel,
            releaseNotes = "Notes for $version",
            minSupported = checkNotNull(SemanticVersion.parse(minSupported)),
            critical = critical,
            linkLabel = linkLabel,
            storeUrl = "https://play.google.com/store/apps/details?id=com.cadence.app",
        ))
    }

    override suspend fun check(current: SemanticVersion): UpdateInfo? {
        callsCompleted += 1
        enqueueFailure?.let { throw it }
        return versions.values
            .filter { it.channel == UpdateChannel.STABLE || true }
            .filter { it.version > current }
            .maxByOrNull { it.version }
    }
}

/** Convenience: an up-to-date source that always reports "no update". */
fun noUpdateSource(): FakeUpdateSource = FakeUpdateSource()
