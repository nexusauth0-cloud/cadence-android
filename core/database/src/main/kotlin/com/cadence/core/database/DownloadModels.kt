package com.cadence.core.database

import com.cadence.core.model.TrackRef

/**
 * Unique identifier for a download record. Internally a [Long] primary key
 * assigned by the persistence layer; externally opaque.
 */
@JvmInline
value class DownloadId(val value: Long) {
    init { require(value > 0) { "DownloadId must be positive" } }
}

/**
 * Lifecycle of a single download (ADR-0007 DOWNLOADS tier).
 *
 *   QUEUED ──► DOWNLOADING ──► COMPLETED
 *       │            │
 *       │            ├──► FAILED ──► QUEUED (retry)
 *       │            └──► CANCELLED ──► QUEUED (retry)
 *       └──► CANCELLED (if cancelled before start)
 *
 * REMOVED is not a state — it means the record is deleted entirely.
 */
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * Categorised failure reason for downloads.
 */
enum class DownloadFailureReason {
    /** Network error while streaming from remote source. */
    NETWORK,
    /** Disk full, write error, or filesystem unavailable. */
    STORAGE,
    /** Source resolution failed before the stream could begin. */
    RESOLUTION,
    /** User cancelled during an active download. */
    CANCELLED,
    /** File present but failed integrity check (zero bytes, truncated, unreadable). */
    INTEGRITY,
    /** Unclassified failure. */
    UNKNOWN,
}

/**
 * Cached display metadata for a download entry (mirrors the CACHE COLUMNS
 * pattern from playlists — ADR-0003). The UI renders these without
 * re-resolving.
 */
data class DownloadMetadata(
    val title: String,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
)

/**
 * A single download record — the full observable state for one track's
 * download lifecycle.
 */
data class DownloadEntry(
    val id: DownloadId,
    val ref: TrackRef,
    val state: DownloadState,
    val progressBytes: Long = 0L,
    val totalBytes: Long? = null,
    val fileSizeBytes: Long? = null,
    val localUri: String? = null,
    val error: DownloadFailureReason? = null,
    val errorMessage: String? = null,
    val meta: DownloadMetadata = DownloadMetadata(title = ""),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isPlayableOffline: Boolean
        get() = state == DownloadState.COMPLETED
            && localUri != null
            && fileSizeBytes != null
            && fileSizeBytes > 0

    /** Human-readable status label for the UI. Never exposes internal tier names. */
    val displayState: String
        get() = when (state) {
            DownloadState.QUEUED -> "Queued"
            DownloadState.DOWNLOADING -> "Downloading"
            DownloadState.COMPLETED -> "Downloaded"
            DownloadState.FAILED -> "Failed"
            DownloadState.CANCELLED -> "Cancelled"
        }
}

/**
 * Result of an offline-availability query for a single [TrackRef].
 * The UI never inspects filesystem paths — this sealed type is the
 * domain-level answer.
 */
sealed interface OfflineAvailability {
    /** File verified: usable for offline playback. */
    data class Available(
        val localUri: String,
        val fileSizeBytes: Long,
    ) : OfflineAvailability

    /** Download in progress — not yet offline-available. */
    data class Downloading(
        val progressBytes: Long,
        val totalBytes: Long?,
    ) : OfflineAvailability

    /** Not enqueued for download. */
    data object NotDownloaded : OfflineAvailability

    /** Download failed — not offline-available. */
    data class Failed(val reason: DownloadFailureReason) : OfflineAvailability

    /** Download was cancelled — not offline-available. */
    data object Cancelled : OfflineAvailability

    /** Downloaded but file missing or corrupted — not offline-available. */
    data object Unavailable : OfflineAvailability
}

/**
 * Aggregate storage report for the Downloads tier, surfaced by the UI
 * storage section (section 14). Uses human-readable units; the UI never
 * reads raw cache tier internals.
 */
data class DownloadStorageReport(
    val downloadCount: Int,
    val totalBytes: Long,
    val budgetBytes: Long,
    /** Approximate free space on the volume holding the download directory. */
    val availableBytes: Long? = null,
) {
    val utilizationPercent: Double
        get() = if (budgetBytes > 0) totalBytes * 100.0 / budgetBytes else 0.0
    val isOverBudget: Boolean get() = totalBytes > budgetBytes

    /** Human-readable total for UI display. */
    val displaySize: String get() = formatBytes(totalBytes)

    companion object {
        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        }
    }
}
