package com.cadence.android.diskcache

import android.content.Context
import com.cadence.core.cache.CacheBudget
import com.cadence.core.cache.CacheTierId
import com.cadence.core.cache.EvictionPolicy
import java.io.File

/**
 * Tuned budgets for the four ADR-0007 tiers (ADR-0007 table).
 *
 * T1 downloads: never auto-evicted (NoOp evictor).
 * T2 stream cache: LRU at a default 512 MB quota.
 * T3 metadata/URL: TTL-based (URLs ~5 min, metadata longer) — rolling TTL is
 *   applied per entry at write time; the budget bounds the on-disk footprint.
 * T4 images: LRU at a default 256 MB quota.
 */
object CacheBudgets {

    const val DEFAULT_STREAM_BYTES = 512L * 1024 * 1024   // 512 MB
    const val DEFAULT_IMAGE_BYTES = 256L * 1024 * 1024    // 256 MB
    const val DEFAULT_METADATA_BYTES = 64L * 1024 * 1024  // 64 MB bound on T3

    fun defaults(): Map<CacheTierId, CacheBudget> = mapOf(
        CacheTierId.DOWNLOADS to CacheBudget(
            tierId = CacheTierId.DOWNLOADS,
            maxBytes = Long.MAX_VALUE,
            evictionPolicy = EvictionPolicy.Never,
        ),
        CacheTierId.STREAM to CacheBudget(
            tierId = CacheTierId.STREAM,
            maxBytes = DEFAULT_STREAM_BYTES,
            evictionPolicy = EvictionPolicy.Lru(DEFAULT_STREAM_BYTES),
        ),
        CacheTierId.METADATA to CacheBudget(
            tierId = CacheTierId.METADATA,
            maxBytes = DEFAULT_METADATA_BYTES,
            evictionPolicy = EvictionPolicy.Ttl(ttlMs = 5 * 60 * 1000L), // 5 min default for URL cache
        ),
        CacheTierId.IMAGES to CacheBudget(
            tierId = CacheTierId.IMAGES,
            maxBytes = DEFAULT_IMAGE_BYTES,
            evictionPolicy = EvictionPolicy.Lru(DEFAULT_IMAGE_BYTES),
        ),
    )
}

/**
 * Constructs the on-disk [FileCacheTierStore] set (ADR-0007) under the app's
 * cache/files directories.
 */
object DiskCacheStore {

    private const val DISK_CACHE_DIR = "cadence_cache"

    /** Build the four persistent tiers. [streamBytes] and [imageBytes] are user tunables. */
    fun build(
        context: Context,
        streamBytes: Long = CacheBudgets.DEFAULT_STREAM_BYTES,
        imageBytes: Long = CacheBudgets.DEFAULT_IMAGE_BYTES,
    ): Map<CacheTierId, FileCacheTierStore> {
        val root = File(context.filesDir, DISK_CACHE_DIR)
        val budgets = CacheBudgets.defaults()
            .map { (tier, b) ->
                when (tier) {
                    CacheTierId.STREAM -> tier to b.copy(maxBytes = streamBytes, evictionPolicy = EvictionPolicy.Lru(streamBytes))
                    CacheTierId.IMAGES -> tier to b.copy(maxBytes = imageBytes, evictionPolicy = EvictionPolicy.Lru(imageBytes))
                    else -> tier to b
                }
            }
            .toMap()

        // Downloads lives apart so "clear cache" (T2/T3/T4) never touches pins.
        return CacheTierId.entries.associateWith { tier ->
            val dir = when (tier) {
                CacheTierId.DOWNLOADS -> File(context.filesDir, "cadence_downloads")
                else -> File(root, tier.name.lowercase())
            }
            FileCacheTierStore(tier, dir, budgets.getValue(tier))
        }
    }
}
