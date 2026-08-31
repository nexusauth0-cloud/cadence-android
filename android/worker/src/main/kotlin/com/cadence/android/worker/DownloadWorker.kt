package com.cadence.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Background worker for a single download (section 6), WorkManager-hosted.
 *
 * Lifecycle: QUEUED → DOWNLOADING → COMPLETED / FAILED / CANCELLED.
 *
 * This class is a T H I N adapter: it resolves the application's download
 * dependencies and delegates the actual resolve→stream→store→verify execution
 * to [DownloadExecutor] (which is unit-testable in isolation). It survives
 * process death via WorkManager + persisted state in Room.
 */
open class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TRACK_REF_PROVIDER = "ref_provider"
        const val KEY_TRACK_REF_NATIVE = "ref_native"
        private const val NOTIFICATION_ID = 1001
        private const val UNIQUE_PREFIX = "cadence_download_"

        /**
         * Enqueue a download for a single track. ExistingWorkPolicy.KEEP makes
         * an in-flight/queued download for the same ref a no-op (dedup).
         */
        fun enqueue(context: Context, downloadId: Long, providerId: String, nativeId: String) {
            val data = Data.Builder()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_TRACK_REF_PROVIDER, providerId)
                .putString(KEY_TRACK_REF_NATIVE, nativeId)
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(providerId, nativeId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Cancel a download by its ref. */
        fun cancel(context: Context, providerId: String, nativeId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(providerId, nativeId))
        }

        private fun uniqueName(providerId: String, nativeId: String): String =
            "$UNIQUE_PREFIX${providerId}_${nativeId}"
    }

    /** Override in tests to inject dependencies. */
    open fun provideRepository(): com.cadence.core.database.DownloadRepository =
        (applicationContext as? DownloadDepsProvider)?.downloadRepository
            ?: throw IllegalStateException("Application must implement DownloadDepsProvider")

    /** Override in tests to inject dependencies. */
    open fun provideResolution(): com.cadence.core.domain.SourceResolutionPort =
        (applicationContext as? DownloadDepsProvider)?.sourceResolution
            ?: throw IllegalStateException("Application must implement DownloadDepsProvider")

    /** Override in tests to inject dependencies. */
    open fun provideStorage(): DownloadStorage =
        (applicationContext as? DownloadDepsProvider)?.downloadStorage
            ?: throw IllegalStateException("Application must implement DownloadDepsProvider")

    /** Override in tests to inject dependencies. */
    open fun provideByteSource(): HttpByteSource =
        (applicationContext as? DownloadDepsProvider)?.httpByteSource
            ?: HttpByteSource()

    override suspend fun doWork(): Result {
        val downloadIdLong = inputData.getLong(KEY_DOWNLOAD_ID, -1)
        val providerId = inputData.getString(KEY_TRACK_REF_PROVIDER) ?: return Result.failure()
        val nativeId = inputData.getString(KEY_TRACK_REF_NATIVE) ?: return Result.failure()
        if (downloadIdLong <= 0) return Result.failure()

        val executor = DownloadExecutor(
            repository = provideRepository(),
            resolution = provideResolution(),
            storage = provideStorage(),
            byteSource = provideByteSource(),
        )

        val outcome = executor.run(
            downloadId = com.cadence.core.database.DownloadId(downloadIdLong),
            providerId = providerId,
            nativeId = nativeId,
            isStopped = { isStopped },
        )

        return when (outcome) {
            is DownloadExecutor.Outcome.Completed -> Result.success()
            is DownloadExecutor.Outcome.Retriable -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            androidx.core.app.NotificationCompat.Builder(
                applicationContext, "cadence_downloads"
            )
                .setContentTitle("Downloading")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build(),
        )
    }
}

/**
 * Interface for the Application to provide download dependencies.
 * Implemented by CadenceApplication.
 */
interface DownloadDepsProvider {
    val downloadRepository: com.cadence.core.database.DownloadRepository
    val sourceResolution: com.cadence.core.domain.SourceResolutionPort
    val downloadStorage: DownloadStorage
    val httpByteSource: HttpByteSource
}
