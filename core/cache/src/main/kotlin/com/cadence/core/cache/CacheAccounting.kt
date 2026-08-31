package com.cadence.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deterministic cache accounting with budget enforcement (ADR-0007 §8).
 *
 * Aggregates across all four tiers and answers:
 * - how much space each tier uses
 * - total cache usage
 * - current budget
 * - whether housekeeping is required
 */
class CacheAccounting(private val budgets: Map<CacheTierId, CacheBudget>) {

    private val mutex = Mutex()
    private val usage = mutableMapOf<CacheTierId, Long>()

    /** Record bytes added to a tier. */
    suspend fun recordAdd(tierId: CacheTierId, bytes: Long) = mutex.withLock {
        usage[tierId] = (usage[tierId] ?: 0) + bytes
    }

    /** Record bytes removed from a tier. */
    suspend fun recordRemove(tierId: CacheTierId, bytes: Long) = mutex.withLock {
        val current = usage[tierId] ?: 0
        usage[tierId] = maxOf(0, current - bytes)
    }

    /** Reset accounting for a tier (after full clear). */
    suspend fun resetTier(tierId: CacheTierId) = mutex.withLock {
        usage[tierId] = 0
    }

    /** Get current usage for a tier. */
    suspend fun usedBytes(tierId: CacheTierId): Long = mutex.withLock {
        usage[tierId] ?: 0
    }

    /** Total usage across all tiers. */
    suspend fun totalUsedBytes(): Long = mutex.withLock {
        usage.values.sum()
    }

    /** Generate a full accounting report. */
    suspend fun report(stores: Map<CacheTierId, CacheTierStore>): CacheAccountingReport =
        mutex.withLock {
            val tierReports = budgets.map { (tierId, budget) ->
                val store = stores[tierId]
                val used = usage[tierId] ?: 0
                TierAccounting(
                    tierId = tierId,
                    entryCount = 0, // Would need store.query; simplified for accounting
                    usedBytes = used,
                    budgetBytes = budget.maxBytes,
                )
            }
            val totalUsed = tierReports.sumOf { it.usedBytes }
            val totalBudget = tierReports.sumOf { it.budgetBytes }
            val anyOverBudget = tierReports.any { it.isOverBudget }

            CacheAccountingReport(
                tiers = tierReports,
                totalUsedBytes = totalUsed,
                totalBudgetBytes = totalBudget,
                housekeepingRequired = anyOverBudget,
            )
        }
}
