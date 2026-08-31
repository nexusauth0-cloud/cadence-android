package com.cadence.android.roomdb

import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.DownloadEntity
import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.DownloadState
import com.cadence.core.database.DownloadStorageReport
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Room-backed [DownloadRepository]. Persists download state through process
 * death, app restart, and device reboot where WorkManager re-enqueues work.
 */
class RoomDownloadRepository private constructor(
    private val database: CadenceDatabase,
    private val dispatcher: CoroutineDispatcher,
    @Suppress("unused") private val clock: Clock,
) : DownloadRepository {

    companion object {
        fun open(context: android.content.Context): RoomDownloadRepository =
            RoomDownloadRepository(
                database = CadenceDatabase.create(context),
                dispatcher = Dispatchers.IO,
                clock = Clock.systemUTC(),
            )

        internal fun forTesting(
            database: CadenceDatabase,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            clock: Clock = Clock.systemUTC(),
        ): RoomDownloadRepository = RoomDownloadRepository(database, dispatcher, clock)
    }

    private val dao get() = database.downloadDao()

    // ── Write ───────────────────────────────────────────────────────

    override suspend fun enqueue(ref: TrackRef, meta: DownloadMetadata): DownloadId =
        withContext(dispatcher) {
            // Deduplicate: if an active (non-completed) entry exists for this ref, reuse it.
            val existing = dao.getActiveByRef(ref.providerId.value, ref.nativeId)
            if (existing != null) return@withContext DownloadId(existing.id)

            // If a completed entry exists, remove it to allow re-download
            // (the unique index on provider_id + native_id prevents duplicates).
            val completed = dao.getByRef(ref.providerId.value, ref.nativeId)
            if (completed != null && completed.state == DownloadState.COMPLETED.name) {
                dao.delete(completed.id)
            }

            val now = System.currentTimeMillis()
            val id = dao.insert(
                DownloadEntity(
                    providerId = ref.providerId.value,
                    nativeId = ref.nativeId,
                    state = DownloadState.QUEUED.name,
                    title = meta.title,
                    artist = meta.artist,
                    durationMs = meta.durationMs,
                    artworkUrl = meta.artworkUrl,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            DownloadId(id)
        }

    override suspend fun markDownloading(id: DownloadId, totalBytes: Long?) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            dao.update(entity.copy(
                state = DownloadState.DOWNLOADING.name,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
            ))
        }

    override suspend fun updateProgress(id: DownloadId, progressBytes: Long, totalBytes: Long?) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            dao.update(entity.copy(
                progressBytes = progressBytes,
                totalBytes = totalBytes ?: entity.totalBytes,
                updatedAt = System.currentTimeMillis(),
            ))
        }

    override suspend fun complete(id: DownloadId, fileSizeBytes: Long, localUri: String) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            dao.update(entity.copy(
                state = DownloadState.COMPLETED.name,
                progressBytes = fileSizeBytes,
                totalBytes = fileSizeBytes,
                fileSizeBytes = fileSizeBytes,
                localUri = localUri,
                errorReason = null,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ))
        }

    override suspend fun fail(id: DownloadId, reason: DownloadFailureReason, message: String?) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            dao.update(entity.copy(
                state = DownloadState.FAILED.name,
                errorReason = reason.name,
                errorMessage = message,
                updatedAt = System.currentTimeMillis(),
            ))
        }

    override suspend fun cancel(id: DownloadId) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            dao.update(entity.copy(
                state = DownloadState.CANCELLED.name,
                updatedAt = System.currentTimeMillis(),
            ))
        }

    override suspend fun remove(id: DownloadId) =
        withContext(dispatcher) {
            dao.delete(id.value)
            Unit
        }

    override suspend fun retry(id: DownloadId) =
        withContext(dispatcher) {
            val entity = dao.getById(id.value) ?: return@withContext
            if (entity.state == DownloadState.FAILED.name || entity.state == DownloadState.CANCELLED.name) {
                dao.update(entity.copy(
                    state = DownloadState.QUEUED.name,
                    progressBytes = 0,
                    errorReason = null,
                    errorMessage = null,
                    localUri = null,
                    fileSizeBytes = null,
                    updatedAt = System.currentTimeMillis(),
                ))
            }
        }

    // ── Read ────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<DownloadEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: DownloadId): Flow<DownloadEntry?> =
        dao.observe(id.value).map { it?.toDomain() }

    override suspend fun get(id: DownloadId): DownloadEntry? =
        withContext(dispatcher) { dao.getById(id.value)?.toDomain() }

    override suspend fun getByRef(ref: TrackRef): DownloadEntry? =
        withContext(dispatcher) { dao.getByRef(ref.providerId.value, ref.nativeId)?.toDomain() }

    override suspend fun offlineAvailability(ref: TrackRef): OfflineAvailability =
        withContext(dispatcher) {
            val entity = dao.getByRef(ref.providerId.value, ref.nativeId)
                ?: return@withContext OfflineAvailability.NotDownloaded
            entity.toOfflineAvailability()
        }

    override suspend fun offlineUri(ref: TrackRef): String? =
        withContext(dispatcher) {
            val entity = dao.getByRef(ref.providerId.value, ref.nativeId)
            if (entity?.state == DownloadState.COMPLETED.name) entity.localUri else null
        }

    override suspend fun storageReport(): DownloadStorageReport =
        withContext(dispatcher) {
            val total = dao.completedTotalBytes() ?: 0L
            DownloadStorageReport(
                downloadCount = dao.completedCount(),
                totalBytes = total,
                budgetBytes = Long.MAX_VALUE,
            )
        }

    override suspend fun completedCount(): Int =
        withContext(dispatcher) { dao.completedCount() }

    override fun observeCompletedRefs(): Flow<List<TrackRef>> =
        dao.observeCompleted().map { list ->
            list.map { TrackRef(ProviderId(it.providerId), it.nativeId) }
        }

    // ── Mapping ─────────────────────────────────────────────────────

    private fun DownloadEntity.toDomain() = DownloadEntry(
        id = DownloadId(id),
        ref = TrackRef(ProviderId(providerId), nativeId),
        state = DownloadState.valueOf(state),
        progressBytes = progressBytes,
        totalBytes = totalBytes,
        fileSizeBytes = fileSizeBytes,
        localUri = localUri,
        error = errorReason?.let { runCatching { DownloadFailureReason.valueOf(it) }.getOrNull() },
        errorMessage = errorMessage,
        meta = DownloadMetadata(
            title = title,
            artist = artist,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DownloadEntity.toOfflineAvailability(): OfflineAvailability =
        when (DownloadState.valueOf(state)) {
            DownloadState.COMPLETED -> {
                if (fileSizeBytes != null && fileSizeBytes > 0 && localUri != null) {
                    OfflineAvailability.Available(localUri, fileSizeBytes)
                } else {
                    OfflineAvailability.Unavailable
                }
            }
            DownloadState.DOWNLOADING -> OfflineAvailability.Downloading(progressBytes, totalBytes)
            DownloadState.QUEUED -> OfflineAvailability.Downloading(0, totalBytes)
            DownloadState.FAILED -> OfflineAvailability.Failed(
                errorReason?.let { runCatching { DownloadFailureReason.valueOf(it) }.getOrNull() }
                    ?: DownloadFailureReason.UNKNOWN,
            )
            DownloadState.CANCELLED -> OfflineAvailability.Cancelled
        }
}
