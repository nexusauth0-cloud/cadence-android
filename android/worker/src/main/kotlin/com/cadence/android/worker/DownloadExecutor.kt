package com.cadence.android.worker

import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadRepository
import com.cadence.core.domain.SourceResolutionPort
import com.cadence.core.model.ResolutionHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Encapsulates a single download execution: resolve → stream → atomic write →
 * integrity-verify → commit. Decoupled from WorkManager so it's unit-testable;
 * [DownloadWorker] is a thin adapter that feeds it cancellation/foreground
 * signals.
 */
class DownloadExecutor(
    private val repository: DownloadRepository,
    private val resolution: SourceResolutionPort,
    private val storage: DownloadStorage,
    private val byteSource: HttpByteSource,
) {

    /**
     * Outcome of one execution. Callers map to WorkManager Result.
     */
    sealed interface Outcome {
        object Completed : Outcome

        /** Retry-worthy transient failure (network/storage/integrity). */
        object Retriable : Outcome
    }

    /**
     * Run a single download. [isStopped] is a suspend predicate evaluated
     * between chunks to honor cancellation.
     *
     * @throws kotlinx.coroutines.CancellationException if [isStopped] returns true.
     */
    suspend fun run(
        downloadId: DownloadId,
        providerId: String,
        nativeId: String,
        isStopped: suspend () -> Boolean,
    ): Outcome = withContext(Dispatchers.IO) {
        val entry = repository.get(downloadId) ?: return@withContext Outcome.Completed

        val key = storage.keyFor(providerId, nativeId)

        // Honour cancellation before/download-time.
        if (isStopped()) {
            repository.cancel(downloadId)
            storage.delete(key)
            return@withContext Outcome.Completed
        }

        try {
            repository.markDownloading(downloadId, totalBytes = null)

            val resolved = withContext(Dispatchers.Default) {
                resolution.resolve(entry.ref, ResolutionHints())
            }

            val streamInfo = byteSource.open(resolved.playbackUri, resolved.httpHeaders)
            val expectedSize = streamInfo.totalBytes
                ?: resolved.metadata.sizeBytes

            repository.updateProgress(downloadId, progressBytes = 0, totalBytes = expectedSize)

            val bytesWritten = try {
                storage.writeStream(
                    key = key,
                    input = streamInfo.stream,
                    onChunk = { bytesWritten ->
                        repository.updateProgress(downloadId, progressBytes = bytesWritten, totalBytes = expectedSize)
                    },
                    isStopped = isStopped,
                )
            } finally {
                streamInfo.onClose?.invoke()
            }

            // Integrity: file must exist, be non-empty, and match expected size.
            if (!storage.verify(key, expectedSize)) {
                repository.fail(downloadId, DownloadFailureReason.INTEGRITY, "File verification failed after write")
                storage.delete(key)
                return@withContext Outcome.Retriable
            }

            val localUri = "file://${storage.fileFor(key).absolutePath}"
            repository.complete(downloadId, fileSizeBytes = bytesWritten, localUri = localUri)
            Outcome.Completed
        } catch (e: kotlinx.coroutines.CancellationException) {
            repository.cancel(downloadId)
            storage.delete(key)
            return@withContext Outcome.Completed
        } catch (e: IOException) {
            repository.fail(downloadId, DownloadFailureReason.NETWORK, e.message)
            Outcome.Retriable
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            repository.fail(downloadId, DownloadFailureReason.UNKNOWN, e.message)
            Outcome.Retriable
        }
    }
}
