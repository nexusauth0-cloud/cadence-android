package com.cadence.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cadence.android.diskcache.CacheBudgets
import com.cadence.android.diskcache.DiskCacheStore
import com.cadence.android.diskcache.FileCacheTierStore
import com.cadence.core.cache.CacheAccounting
import com.cadence.core.cache.CacheHousekeeper
import com.cadence.core.cache.CacheTierId
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic cache housekeeper (ADR-0007 §3, §7).
 *
 * Runs every 24h and:
 *  - prunes expired T3 metadata/URL entries,
 *  - evicts over-budget T2 stream / T4 image tiers,
 *  - caps the database backup folder to the last N copies,
 *  - clears stale temporary files left behind by interrupted writes.
 *
 * This worker owns no UI; it is a thin shell over [CacheHousekeeper], keeping
 * ADR-0006 §5 "service stays thin" hygiene at the process level too.
 */
open class CacheHousekeepingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_NAME = "cadence_cache_housekeeping"
        private const val KEEP_BACKUPS = 3

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CacheHousekeepingWorker>(
                24,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    /** The on-disk tiers to maintain. Overridable in tests. */
    open fun tiers(): Map<CacheTierId, FileCacheTierStore> =
        DiskCacheStore.build(applicationContext)

    /** Folder holding database backup copies. Overridable in tests. */
    open fun backupDirectory(): File =
        File(applicationContext.filesDir, "database_backups")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val storeTiers = tiers()
            val accounting = CacheAccounting(
                CacheBudgets.defaults()
                    .values
                    .associateBy { it.tierId },
            )
            val housekeeper = CacheHousekeeper(storeTiers, accounting)
            housekeeper.run()

            capBackupFolder()

            Result.success()
        }.getOrElse { Result.retry() }
    }

    /** Keep only the newest database backups (ADR-0007 §3: cap at N=3). */
    private fun capBackupFolder() {
        val backups = backupDirectory().listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        backups.drop(KEEP_BACKUPS).forEach { it.delete() }
    }
}
