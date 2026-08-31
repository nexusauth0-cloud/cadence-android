package com.cadence.core.database

import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DownloadRepository] for JVM tests. Hermetic — no disk, no network.
 */
class InMemoryDownloadRepository : DownloadRepository {

    private val mutex = Any()
    private val entries = mutableMapOf<Long, DownloadEntry>()
    private var nextId = 1L

    private val _all = MutableStateFlow<List<DownloadEntry>>(emptyList())
    private val all: StateFlow<List<DownloadEntry>> = _all.asStateFlow()

    private fun emitAll() {
        _all.value = entries.values.sortedByDescending { it.createdAt }.toList()
    }

    override suspend fun enqueue(ref: TrackRef, meta: DownloadMetadata): DownloadId = synchronized(mutex) {
        // Deduplicate: if already enqueued/active/failed/cancelled for this ref, reuse.
        val existing = entries.values.find { it.ref == ref && it.state != DownloadState.COMPLETED }
        if (existing != null) return@synchronized existing.id

        // Remove completed entries to allow re-download (consistent with Room).
        entries.values.find { it.ref == ref && it.state == DownloadState.COMPLETED }
            ?.let { entries.remove(it.id.value) }

        val id = DownloadId(nextId++)
        entries[id.value] = DownloadEntry(
            id = id,
            ref = ref,
            state = DownloadState.QUEUED,
            meta = meta,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        emitAll()
        id
    }

    override suspend fun markDownloading(id: DownloadId, totalBytes: Long?) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            entries[id.value] = entry.copy(
                state = DownloadState.DOWNLOADING,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
            )
            emitAll()
        }
    }

    override suspend fun updateProgress(id: DownloadId, progressBytes: Long, totalBytes: Long?) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            entries[id.value] = entry.copy(
                progressBytes = progressBytes,
                totalBytes = totalBytes ?: entry.totalBytes,
                updatedAt = System.currentTimeMillis(),
            )
            emitAll()
        }
    }

    override suspend fun complete(id: DownloadId, fileSizeBytes: Long, localUri: String) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            entries[id.value] = entry.copy(
                state = DownloadState.COMPLETED,
                progressBytes = fileSizeBytes,
                totalBytes = fileSizeBytes,
                fileSizeBytes = fileSizeBytes,
                localUri = localUri,
                error = null,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            )
            emitAll()
        }
    }

    override suspend fun fail(id: DownloadId, reason: DownloadFailureReason, message: String?) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            entries[id.value] = entry.copy(
                state = DownloadState.FAILED,
                error = reason,
                errorMessage = message,
                updatedAt = System.currentTimeMillis(),
            )
            emitAll()
        }
    }

    override suspend fun cancel(id: DownloadId) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            entries[id.value] = entry.copy(
                state = DownloadState.CANCELLED,
                updatedAt = System.currentTimeMillis(),
            )
            emitAll()
        }
    }

    override suspend fun remove(id: DownloadId) {
        synchronized(mutex) {
            entries.remove(id.value)
            emitAll()
        }
    }

    override suspend fun retry(id: DownloadId) {
        synchronized(mutex) {
            val entry = entries[id.value] ?: return@synchronized
            if (entry.state == DownloadState.FAILED || entry.state == DownloadState.CANCELLED) {
                entries[id.value] = entry.copy(
                    state = DownloadState.QUEUED,
                    progressBytes = 0,
                    error = null,
                    errorMessage = null,
                    localUri = null,
                    fileSizeBytes = null,
                    updatedAt = System.currentTimeMillis(),
                )
                emitAll()
            }
        }
    }

    override fun observeAll(): Flow<List<DownloadEntry>> = all

    override fun observe(id: DownloadId): Flow<DownloadEntry?> = all.map { list ->
        list.find { it.id == id }
    }

    override suspend fun get(id: DownloadId): DownloadEntry? = synchronized(mutex) {
        entries[id.value]
    }

    override suspend fun getByRef(ref: TrackRef): DownloadEntry? = synchronized(mutex) {
        entries.values.find { it.ref == ref }
    }

    override suspend fun offlineAvailability(ref: TrackRef): OfflineAvailability = synchronized(mutex) {
        val entry = entries.values.find { it.ref == ref } ?: return@synchronized OfflineAvailability.NotDownloaded
        when (entry.state) {
            DownloadState.COMPLETED -> {
                if (entry.isPlayableOffline) {
                    OfflineAvailability.Available(entry.localUri!!, entry.fileSizeBytes!!)
                } else {
                    OfflineAvailability.Unavailable
                }
            }
            DownloadState.DOWNLOADING -> OfflineAvailability.Downloading(entry.progressBytes, entry.totalBytes)
            DownloadState.QUEUED -> OfflineAvailability.Downloading(0, entry.totalBytes)
            DownloadState.FAILED -> OfflineAvailability.Failed(entry.error ?: DownloadFailureReason.UNKNOWN)
            DownloadState.CANCELLED -> OfflineAvailability.Cancelled
        }
    }

    override suspend fun offlineUri(ref: TrackRef): String? = synchronized(mutex) {
        entries.values.find { it.ref == ref && it.state == DownloadState.COMPLETED }?.localUri
    }

    override suspend fun storageReport(): DownloadStorageReport = synchronized(mutex) {
        val completed = entries.values.filter { it.state == DownloadState.COMPLETED }
        DownloadStorageReport(
            downloadCount = completed.size,
            totalBytes = completed.sumOf { it.fileSizeBytes ?: 0L },
            budgetBytes = Long.MAX_VALUE,
        )
    }

    override suspend fun completedCount(): Int = synchronized(mutex) {
        entries.values.count { it.state == DownloadState.COMPLETED }
    }

    override fun observeCompletedRefs(): Flow<List<TrackRef>> = all.map { list ->
        list.filter { it.state == DownloadState.COMPLETED }.map { it.ref }
    }
}
