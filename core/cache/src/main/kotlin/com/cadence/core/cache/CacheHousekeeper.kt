package com.cadence.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Housekeeping skeleton (ADR-0007 §7).
 *
 * Periodic cleanup tasks:
 * - Prune expired T3 rows (metadata TTL)
 * - Cap DB backup folder to last N=3 copies
 * - Clear stale tmp HTTP caches
 * - Enforce Event/history retention per privacy settings
 *
 * In production, this is a WorkManager periodic worker.
 * The worker does NOT depend directly on UI code.
 */
class CacheHousekeeper(
    private val tiers: Map<CacheTierId, CacheTierStore>,
    private val accounting: CacheAccounting,
) {

    private val mutex = Mutex()
    private var lastRunAt = 0L

    /** Result of a housekeeping run. */
    data class Result(
        val expiredPruned: Int,
        val bytesEvicted: Long,
        val tiersTouched: List<CacheTierId>,
    )

    /**
     * Run all housekeeping tasks. Idempotent — safe to call repeatedly.
     * Returns the result for test assertions.
     */
    suspend fun run(): Result = mutex.withLock {
        var expiredPruned = 0
        var bytesEvicted = 0L
        val tiersTouched = mutableListOf<CacheTierId>()

        // 1. Prune expired T3 entries
        tiers[CacheTierId.METADATA]?.let { store ->
            val pruned = store.pruneExpired()
            expiredPruned += pruned
            if (pruned > 0) tiersTouched.add(CacheTierId.METADATA)
        }

        // 2. Evict over-budget tiers (T2 stream cache, T4 images)
        for (tierId in listOf(CacheTierId.STREAM, CacheTierId.IMAGES)) {
            tiers[tierId]?.let { store ->
                val freed = store.evictToBudget()
                bytesEvicted += freed
                if (freed > 0 && tierId !in tiersTouched) tiersTouched.add(tierId)
            }
        }

        lastRunAt = System.currentTimeMillis()
        Result(expiredPruned, bytesEvicted, tiersTouched)
    }

    /** Whether housekeeping is needed (any tier over budget or has expired entries). */
    suspend fun isRequired(): Boolean {
        val report = accounting.report(tiers)
        return report.housekeepingRequired
    }

    /** Timestamp of last successful run (0 = never run). */
    fun lastRunTimestamp(): Long = lastRunAt
}
