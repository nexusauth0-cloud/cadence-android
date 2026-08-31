package com.cadence.core.database

import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow

/**
 * Port for download state persistence (ADR-0007 DOWNLOADS tier).
 *
 * The UI observes download entries via [observeAll]/[observe]; the worker
 * mutates state via the suspend methods. Storage implementation details
 * (file paths, temp files, cache tiers) never reach the UI — the port
 * answers with [OfflineAvailability] and [DownloadStorageReport] only.
 *
 * Duplicate-download prevention: [enqueue] checks for an existing entry
 * with the same [TrackRef]. If one exists (QUEUED/DOWNLOADING/FAILED/
 * CANCELLED), it retries rather than creating a duplicate.
 */
interface DownloadRepository {

    // ── Write (worker / use-case driven) ────────────────────────────

    /**
     * Enqueue a new download. Returns the [DownloadId].
     * If a download for the same [TrackRef] already exists and is
     * QUEUED/DOWNLOADING/FAILED/CANCELLED, returns the existing id
     * (idempotent).
     */
    suspend fun enqueue(ref: TrackRef, meta: DownloadMetadata): DownloadId

    /** Transition to DOWNLOADING with initial progress. */
    suspend fun markDownloading(id: DownloadId, totalBytes: Long?)

    /** Update download progress. */
    suspend fun updateProgress(id: DownloadId, progressBytes: Long, totalBytes: Long?)

    /** Mark download complete — file verified and durable. */
    suspend fun complete(id: DownloadId, fileSizeBytes: Long, localUri: String)

    /** Mark download failed. */
    suspend fun fail(id: DownloadId, reason: DownloadFailureReason, message: String?)

    /** Transition to CANCELLED (record kept, file cleaned up by caller). */
    suspend fun cancel(id: DownloadId)

    /** Fully remove the record (delete row + file via caller). */
    suspend fun remove(id: DownloadId)

    /** Retry a failed or cancelled download: transition back to QUEUED. */
    suspend fun retry(id: DownloadId)

    // ── Read (use-case / UI driven) ─────────────────────────────────

    /** Observe all downloads, most recently enqueued first. */
    fun observeAll(): Flow<List<DownloadEntry>>

    /** Observe a single download entry. */
    fun observe(id: DownloadId): Flow<DownloadEntry?>

    /** One-shot lookup. */
    suspend fun get(id: DownloadId): DownloadEntry?

    /** Look up by track ref — for deduplication and offline-availability queries. */
    suspend fun getByRef(ref: TrackRef): DownloadEntry?

    /** Check offline availability for a track. */
    suspend fun offlineAvailability(ref: TrackRef): OfflineAvailability

    /** Return the local playback URI for a completed, verified download. */
    suspend fun offlineUri(ref: TrackRef): String?

    /** Aggregate storage report for the UI storage section. */
    suspend fun storageReport(): DownloadStorageReport

    /** Number of completed (offline-available) downloads. */
    suspend fun completedCount(): Int

    /** All completed download refs — used for the offline-only library filter. */
    fun observeCompletedRefs(): Flow<List<TrackRef>>
}
