package com.cadence.core.cache

/**
 * Four named cache tiers (ADR-0007).
 * Each tier has a distinct ownership, eviction policy, and storage medium.
 */
enum class CacheTierId {
    /** T1: Pinned audio downloads. Never auto-evicted. */
    DOWNLOADS,
    /** T2: Stream cache. LRU evicted at quota. */
    STREAM,
    /** T3: Metadata/URL caches. TTL-based eviction. */
    METADATA,
    /** T4: Image cache. Coil disk + RAM LRU. */
    IMAGES,
}

/**
 * Budget configuration for a single cache tier.
 */
data class CacheBudget(
    val tierId: CacheTierId,
    val maxBytes: Long,
    val evictionPolicy: EvictionPolicy,
)

sealed interface EvictionPolicy {
    /** Never evict (downloads). */
    data object Never : EvictionPolicy

    /** LRU eviction when tier exceeds [maxBytes]. */
    data class Lru(val maxBytes: Long) : EvictionPolicy

    /** TTL-based: entries older than [ttlMs] are pruned. */
    data class Ttl(val ttlMs: Long) : EvictionPolicy
}

/**
 * A single cached entry.
 */
data class CacheEntry(
    val key: String,
    val tierId: CacheTierId,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
) {
    fun isExpired(now: Long): Boolean = expiresAt != null && now > expiresAt
}

/**
 * Per-tier accounting snapshot.
 */
data class TierAccounting(
    val tierId: CacheTierId,
    val entryCount: Int,
    val usedBytes: Long,
    val budgetBytes: Long,
) {
    val utilizationPercent: Double get() = if (budgetBytes > 0) usedBytes * 100.0 / budgetBytes else 0.0
    val isOverBudget: Boolean get() = usedBytes > budgetBytes
}

/**
 * Aggregate accounting across all tiers.
 */
data class CacheAccountingReport(
    val tiers: List<TierAccounting>,
    val totalUsedBytes: Long,
    val totalBudgetBytes: Long,
    val housekeepingRequired: Boolean,
)
