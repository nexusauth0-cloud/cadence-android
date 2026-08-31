package com.cadence.core.domain

import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef

/**
 * Source-selection policy (section 9) for playback:
 *
 *   1. usable local downloaded source (offline)
 *   2. remote source resolution
 *   3. unavailable
 *
 * Wraps a remote [SourceResolutionPort] and prefers a verified local download
 * when one exists. When [offlineOnly] is requested, networking is never
 * attempted — a non-downloaded track is honestly unavailable.
 *
 * This is the single domain abstraction the UI playback path consults; the UI
 * never inspects filesystem paths or cache tier internals.
 */
class OfflineFirstResolutionPort(
    private val downloads: DownloadRepository,
    private val remote: SourceResolutionPort,
) : SourceResolutionPort {

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource {
        val availability = downloads.offlineAvailability(ref)
        return when (availability) {
            is OfflineAvailability.Available -> {
                // Prefer local: return a local ResolvedSource (file:// URI).
                // No remote networking.
                localSource(ref, availability)
            }
            is OfflineAvailability.Downloading,
            is OfflineAvailability.Cancelled,
            is OfflineAvailability.Failed,
            OfflineAvailability.NotDownloaded -> {
                if (hints.offlineOnly) {
                    // Honest unavailable: cannot network while offline-mode active.
                    throw SourceErrorUnavailable()
                }
                remote.resolve(ref, hints)
            }
            OfflineAvailability.Unavailable -> {
                if (hints.offlineOnly) throw SourceErrorUnavailable()
                remote.resolve(ref, hints)
            }
        }
    }

    private fun localSource(ref: TrackRef, a: OfflineAvailability.Available): ResolvedSource =
        ResolvedSource(
            ref = ref,
            playbackUri = a.localUri,
            resolutionId = "offline-${ref.providerId.value}-${ref.nativeId}",
            metadata = SourceMetadata(
                sizeBytes = a.fileSizeBytes,
                container = null,
            ),
            renditionLabel = "offline",
        )
}

/**
 * Thrown when offline mode is active and no usable local source exists.
 * Distinct from provider SourceError so playback can surface the honest
 * "not available offline" state without attempting networking.
 */
class SourceErrorUnavailable :
    Exception("This track isn't available offline")

/**
 * Use case: resolve the playable source for a track, preferring offline.
 * Backs the shared playback path.
 */
class ResolveForPlaybackUseCase(
    private val resolution: SourceResolutionPort,
) {
    suspend operator fun invoke(track: Track, offlineOnly: Boolean = false): ResolvedSource =
        resolution.resolve(track.ref, ResolutionHints(offlineOnly = offlineOnly))
}
