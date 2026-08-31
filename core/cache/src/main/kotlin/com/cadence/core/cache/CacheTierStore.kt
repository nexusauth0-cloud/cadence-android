package com.cadence.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Async cache tier interface (ADR-0007).
 * Avoids blocking; exposes deterministic behavior for testing.
 */
interface CacheTierStore {
    val tierId: CacheTierId

    /** Store an entry. If over budget, evict per policy before insert. */
    suspend fun put(entry: CacheEntry)

    /** Retrieve an entry by key. Returns null if absent/expired. Updates access time. */
    suspend fun get(key: String): CacheEntry?

    /** Remove a specific entry. */
    suspend fun remove(key: String): Boolean

    /** Remove all entries for this tier. */
    suspend fun clear()

    /** Current accounting for this tier. */
    suspend fun accounting(): TierAccounting

    /** Prune expired entries (for TTL tiers). Returns number removed. */
    suspend fun pruneExpired(): Int

    /** Evict entries until under budget. Returns bytes freed. */
    suspend fun evictToBudget(): Long

    /** All entries (for debugging/testing). */
    suspend fun entries(): List<CacheEntry>
}

/**
 * In-memory [CacheTierStore] for JVM tests.
 */
class InMemoryCacheTier(
    override val tierId: CacheTierId,
    private val budget: CacheBudget,
    /** If true, LRU eviction only happens via explicit [evictToBudget] calls,
     *  not during [put]. Used by housekeeper tests to set up over-budget state. */
    private val deferEviction: Boolean = false,
) : CacheTierStore {

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, CacheEntry>()

    override suspend fun put(entry: CacheEntry): Unit = mutex.withLock {
        entries[entry.key] = entry
        // Only LRU budget eviction happens on insert (unless deferred for testing);
        // TTL pruning is explicit (via pruneExpired or the housekeeper).
        if (!deferEviction && budget.evictionPolicy is EvictionPolicy.Lru) {
            evictLruToBudget()
        }
    }

    override suspend fun get(key: String): CacheEntry? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (entry.isExpired(System.currentTimeMillis())) {
            entries.remove(key)
            null
        } else {
            val updated = entry.copy(lastAccessedAt = System.currentTimeMillis())
            entries[key] = updated
            updated
        }
    }

    override suspend fun remove(key: String): Boolean = mutex.withLock {
        entries.remove(key) != null
    }

    override suspend fun clear() = mutex.withLock {
        entries.clear()
    }

    override suspend fun accounting(): TierAccounting = mutex.withLock {
        TierAccounting(
            tierId = tierId,
            entryCount = entries.size,
            usedBytes = entries.values.sumOf { it.sizeBytes },
            budgetBytes = budget.maxBytes,
        )
    }

    override suspend fun pruneExpired(): Int = mutex.withLock { pruneExpiredInternal() }

    override suspend fun evictToBudget(): Long = mutex.withLock { evictLruToBudget() }

    override suspend fun entries(): List<CacheEntry> = mutex.withLock {
        entries.values.toList()
    }

    private fun pruneExpiredInternal(): Int {
        val now = System.currentTimeMillis()
        val expired = entries.filter { it.value.isExpired(now) }
        expired.keys.forEach { entries.remove(it) }
        return expired.size
    }

    private fun evictLruToBudget(): Long {
        val maxBytes = when (val p = budget.evictionPolicy) {
            is EvictionPolicy.Lru -> p.maxBytes
            else -> budget.maxBytes
        }
        var freed = 0L
        while (entries.values.sumOf { it.sizeBytes } > maxBytes && entries.isNotEmpty()) {
            val oldest = entries.values.minByOrNull { it.lastAccessedAt } ?: break
            entries.remove(oldest.key)
            freed += oldest.sizeBytes
        }
        return freed
    }
}
