package com.cadence.android.worker

import android.content.Context
import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.DownloadStorageReport
import com.cadence.core.database.DownloadState
import com.cadence.core.domain.DownloadOperationsPort
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow

/**
 * Application-level download orchestrator (section 6, 12).
 *
 * Provides a clean API for the ViewModel/UI to interact with downloads.
 * Delegates persistence to [DownloadRepository] and background execution to
 * [DownloadWorker] (WorkManager). Implements [DownloadOperationsPort] so the
 * UI depends only on the domain seam — never on WorkManager, filesystem paths,
 * or byte streams.
 */
class DownloadService(
    private val repository: DownloadRepository,
    private val context: Context,
) : DownloadOperationsPort {

    /** Observe all downloads (for the Downloads screen). */
    fun observeDownloads(): Flow<List<DownloadEntry>> = repository.observeAll()

    /** Observe a single download entry. */
    fun observeDownload(id: DownloadId): Flow<DownloadEntry?> = repository.observe(id)

    /** All completed download refs (for offline-only library filter). */
    fun observeCompletedRefs(): Flow<List<TrackRef>> = repository.observeCompletedRefs()

    /** Offline availability for the UI. */
    suspend fun offlineAvailability(ref: TrackRef) = repository.offlineAvailability(ref)

    /** Storage report for the Downloads storage section. */
    suspend fun storageReport(): DownloadStorageReport = repository.storageReport()

    /** Number of completed downloads. */
    suspend fun completedCount(): Int = repository.completedCount()

    // ---- DownloadOperationsPort ----

    override suspend fun download(track: Track): Long {
        val id = repository.enqueue(
            track.ref,
            DownloadMetadata(
                title = track.title,
                artist = track.artists.firstOrNull()?.name,
                durationMs = track.durationMs,
                artworkUrl = track.artwork?.variants?.firstOrNull()?.url,
            ),
        )
        DownloadWorker.enqueue(
            context = context,
            downloadId = id.value,
            providerId = track.ref.providerId.value,
            nativeId = track.ref.nativeId,
        )
        return id.value
    }

    override suspend fun cancel(downloadId: Long) {
        val id = DownloadId(downloadId)
        val entry = repository.get(id) ?: return
        DownloadWorker.cancel(context, entry.ref.providerId.value, entry.ref.nativeId)
        repository.cancel(id)
    }

    override suspend fun remove(downloadId: Long) {
        val id = DownloadId(downloadId)
        val entry = repository.get(id) ?: return
        if (entry.state == DownloadState.COMPLETED) {
            val uri = entry.localUri
            if (uri != null) {
                // Delete the actual durable file.
                val path = uri.removePrefix("file://")
                java.io.File(path).delete()
            }
        }
        repository.remove(id)
    }

    override suspend fun retry(downloadId: Long) {
        val id = DownloadId(downloadId)
        val entry = repository.get(id) ?: return
        repository.retry(id)
        DownloadWorker.enqueue(
            context = context,
            downloadId = id.value,
            providerId = entry.ref.providerId.value,
            nativeId = entry.ref.nativeId,
        )
    }

    override suspend fun downloadAll(tracks: List<Track>): Int {
        var started = 0
        for (track in tracks) {
            val existing = repository.getByRef(track.ref)
            if (existing == null || existing.state == DownloadState.COMPLETED) continue
            download(track)
            started++
        }
        return started
    }
}
