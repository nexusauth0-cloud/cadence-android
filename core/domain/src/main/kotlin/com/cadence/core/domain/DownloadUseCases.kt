package com.cadence.core.domain

import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.DownloadStorageReport
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow

/** Enqueue a download for a single track. Returns the id. */
class EnqueueDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(track: Track): DownloadId {
        val meta = DownloadMetadata(
            title = track.title,
            artist = track.artists.firstOrNull()?.name,
            durationMs = track.durationMs,
            artworkUrl = track.artwork?.variants?.firstOrNull()?.url,
        )
        return repository.enqueue(track.ref, meta)
    }
}

/** Observe all download entries. */
class ObserveDownloadsUseCase(private val repository: DownloadRepository) {
    operator fun invoke(): Flow<List<DownloadEntry>> = repository.observeAll()
}

/** Observe a single download entry. */
class ObserveDownloadUseCase(private val repository: DownloadRepository) {
    operator fun invoke(id: DownloadId): Flow<DownloadEntry?> = repository.observe(id)
}

/** Cancel an in-progress download. */
class CancelDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = repository.cancel(id)
}

/** Remove a download (record + file cleanup is the caller's job). */
class RemoveDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = repository.remove(id)
}

/** Retry a failed or cancelled download. */
class RetryDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = repository.retry(id)
}

/** Inspect download status / offline availability for a track. */
class DownloadStatusUseCase(private val repository: DownloadRepository) {
    suspend fun entry(ref: TrackRef): DownloadEntry? = repository.getByRef(ref)
    suspend fun offlineAvailability(ref: TrackRef) = repository.offlineAvailability(ref)
}

/** Aggregate storage report for the Downloads storage section. */
class DownloadStorageUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(): DownloadStorageReport = repository.storageReport()
}

/** All completed download refs (offline-only library filter). */
class ObserveCompletedDownloadsUseCase(private val repository: DownloadRepository) {
    operator fun invoke(): Flow<List<TrackRef>> = repository.observeCompletedRefs()
}
