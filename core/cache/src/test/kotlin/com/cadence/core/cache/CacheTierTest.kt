package com.cadence.core.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheTierTest {


    private fun lruTier(id: CacheTierId, maxBytes: Long) =
        InMemoryCacheTier(id, CacheBudget(id, maxBytes, EvictionPolicy.Lru(maxBytes)))

    private fun ttlTier(id: CacheTierId, ttlMs: Long) =
        InMemoryCacheTier(id, CacheBudget(id, Long.MAX_VALUE, EvictionPolicy.Ttl(ttlMs)))

    private fun entry(key: String, sizeBytes: Long = 100, tierId: CacheTierId = CacheTierId.STREAM) =
        CacheEntry(key = key, tierId = tierId, sizeBytes = sizeBytes)

    @Test
    fun `put and get round trip`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        store.put(entry("k1", 100))
        val e = store.get("k1")
        assertNotNull(e)
        assertEquals("k1", e.key)
        assertEquals(100, e.sizeBytes)
    }

    @Test
    fun `get returns null for missing key`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        assertNull(store.get("missing"))
    }

    @Test
    fun `remove returns true for existing`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        store.put(entry("k1"))
        assertTrue(store.remove("k1"))
        assertNull(store.get("k1"))
    }

    @Test
    fun `remove returns false for missing`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        assertFalse(store.remove("ghost"))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        store.put(entry("a"))
        store.put(entry("b"))
        store.clear()
        assertEquals(0, store.accounting().entryCount)
    }

    @Test
    fun `LRU eviction when over budget`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 250)
        store.put(entry("a", 100))
        store.put(entry("b", 100))
        // Now at 200, under budget
        store.put(entry("c", 100)) // 300 > 250 → evict oldest
        val accounting = store.accounting()
        assertTrue(accounting.usedBytes <= 250)
        // "a" should be evicted (oldest by lastAccessedAt)
        assertNull(store.get("a"))
    }

    @Test
    fun `TTL expiration prunes expired entries`() = runTest {
        val store = ttlTier(CacheTierId.METADATA, ttlMs = 1)
        store.put(CacheEntry("k1", CacheTierId.METADATA, 100, expiresAt = 1L))
        Thread.sleep(10)
        val pruned = store.pruneExpired()
        assertEquals(1, pruned)
        assertNull(store.get("k1"))
    }

    @Test
    fun `accounting reflects current state`() = runTest {
        val store = lruTier(CacheTierId.IMAGES, 500)
        store.put(entry("a", 100, CacheTierId.IMAGES))
        store.put(entry("b", 200, CacheTierId.IMAGES))
        val acc = store.accounting()
        assertEquals(2, acc.entryCount)
        assertEquals(300, acc.usedBytes)
        assertEquals(500, acc.budgetBytes)
        assertFalse(acc.isOverBudget)
    }

    @Test
    fun `entries() returns all current entries`() = runTest {
        val store = lruTier(CacheTierId.STREAM, 1000)
        store.put(entry("x"))
        store.put(entry("y"))
        val entries = store.entries()
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.key == "x" })
        assertTrue(entries.any { it.key == "y" })
    }

    @Test
    fun `never-evict tier does not evict`() = runTest {
        val store = InMemoryCacheTier(
            CacheTierId.DOWNLOADS,
            CacheBudget(CacheTierId.DOWNLOADS, 100, EvictionPolicy.Never)
        )
        store.put(entry("a", 100))
        store.put(entry("b", 100))
        store.put(entry("c", 100))
        // All three should remain despite being over budget
        assertEquals(3, store.accounting().entryCount)
    }
}

class CacheAccountingTest {

    @Test
    fun `below budget reports no housekeeping`() = runTest {
        val budgets = mapOf(
            CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 1000, EvictionPolicy.Lru(1000)),
        )
        val accounting = CacheAccounting(budgets)
        accounting.recordAdd(CacheTierId.STREAM, 500)
        assertEquals(500, accounting.usedBytes(CacheTierId.STREAM))
        assertEquals(500, accounting.totalUsedBytes())
    }

    @Test
    fun `over budget reports housekeeping required`() = runTest {
        val budgets = mapOf(
            CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 100, EvictionPolicy.Lru(100)),
        )
        val accounting = CacheAccounting(budgets)
        accounting.recordAdd(CacheTierId.STREAM, 200)
        val store = InMemoryCacheTier(CacheTierId.STREAM, budgets[CacheTierId.STREAM]!!)
        val report = accounting.report(mapOf(CacheTierId.STREAM to store))
        assertTrue(report.housekeepingRequired)
    }

    @Test
    fun `multiple tiers compete for cleanup`() = runTest {
        val budgets = mapOf(
            CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 100, EvictionPolicy.Lru(100)),
            CacheTierId.IMAGES to CacheBudget(CacheTierId.IMAGES, 100, EvictionPolicy.Lru(100)),
        )
        val accounting = CacheAccounting(budgets)
        accounting.recordAdd(CacheTierId.STREAM, 150)
        accounting.recordAdd(CacheTierId.IMAGES, 80)
        assertEquals(230, accounting.totalUsedBytes())
        val streamStore = InMemoryCacheTier(CacheTierId.STREAM, budgets[CacheTierId.STREAM]!!)
        val imageStore = InMemoryCacheTier(CacheTierId.IMAGES, budgets[CacheTierId.IMAGES]!!)
        val report = accounting.report(mapOf(CacheTierId.STREAM to streamStore, CacheTierId.IMAGES to imageStore))
        assertTrue(report.housekeepingRequired) // stream is over
    }

    @Test
    fun `recordRemove decreases usage`() = runTest {
        val budgets = mapOf(
            CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 1000, EvictionPolicy.Lru(1000)),
        )
        val accounting = CacheAccounting(budgets)
        accounting.recordAdd(CacheTierId.STREAM, 300)
        accounting.recordRemove(CacheTierId.STREAM, 200)
        assertEquals(100, accounting.usedBytes(CacheTierId.STREAM))
    }

    @Test
    fun `resetTier zeros out usage`() = runTest {
        val budgets = mapOf(
            CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 1000, EvictionPolicy.Lru(1000)),
        )
        val accounting = CacheAccounting(budgets)
        accounting.recordAdd(CacheTierId.STREAM, 500)
        accounting.resetTier(CacheTierId.STREAM)
        assertEquals(0, accounting.usedBytes(CacheTierId.STREAM))
    }
}

class CacheHousekeeperTest {

    @Test
    fun `housekeeping prunes expired metadata entries`() = runTest {
        val metadataTier = InMemoryCacheTier(
            CacheTierId.METADATA,
            CacheBudget(CacheTierId.METADATA, Long.MAX_VALUE, EvictionPolicy.Ttl(1))
        )
        metadataTier.put(CacheEntry("expired1", CacheTierId.METADATA, 100, expiresAt = 1L))
        metadataTier.put(CacheEntry("expired2", CacheTierId.METADATA, 100, expiresAt = 1L))

        val budgets = mapOf(CacheTierId.METADATA to CacheBudget(CacheTierId.METADATA, Long.MAX_VALUE, EvictionPolicy.Ttl(1)))
        val accounting = CacheAccounting(budgets)
        val housekeeper = CacheHousekeeper(mapOf(CacheTierId.METADATA to metadataTier), accounting)

        val result = housekeeper.run()
        assertEquals(2, result.expiredPruned)
        assertTrue(CacheTierId.METADATA in result.tiersTouched)
    }

    @Test
    fun `housekeeping evicts over-budget stream cache`() = runTest {
        val streamTier = InMemoryCacheTier(
            CacheTierId.STREAM,
            CacheBudget(CacheTierId.STREAM, 200, EvictionPolicy.Lru(200)),
            deferEviction = true,
        )
        streamTier.put(CacheEntry("a", CacheTierId.STREAM, 100))
        streamTier.put(CacheEntry("b", CacheTierId.STREAM, 100))
        streamTier.put(CacheEntry("c", CacheTierId.STREAM, 100)) // 300 > 200

        val budgets = mapOf(CacheTierId.STREAM to CacheBudget(CacheTierId.STREAM, 200, EvictionPolicy.Lru(200)))
        val accounting = CacheAccounting(budgets)
        val housekeeper = CacheHousekeeper(mapOf(CacheTierId.STREAM to streamTier), accounting)

        val result = housekeeper.run()
        assertTrue(result.bytesEvicted > 0)
        assertTrue(CacheTierId.STREAM in result.tiersTouched)
        assertTrue(streamTier.accounting().usedBytes <= 200)
    }

    @Test
    fun `double housekeeping run is idempotent`() = runTest {
        val metadataTier = InMemoryCacheTier(
            CacheTierId.METADATA,
            CacheBudget(CacheTierId.METADATA, Long.MAX_VALUE, EvictionPolicy.Ttl(1))
        )
        metadataTier.put(CacheEntry("e1", CacheTierId.METADATA, 100, expiresAt = 1L))

        val budgets = mapOf(CacheTierId.METADATA to CacheBudget(CacheTierId.METADATA, Long.MAX_VALUE, EvictionPolicy.Ttl(1)))
        val accounting = CacheAccounting(budgets)
        val housekeeper = CacheHousekeeper(mapOf(CacheTierId.METADATA to metadataTier), accounting)

        val r1 = housekeeper.run()
        assertEquals(1, r1.expiredPruned)
        val r2 = housekeeper.run()
        assertEquals(0, r2.expiredPruned) // nothing left to prune
    }

    @Test
    fun `lastRunTimestamp updates after run`() = runTest {
        val budgets = emptyMap<CacheTierId, CacheBudget>()
        val accounting = CacheAccounting(budgets)
        val housekeeper = CacheHousekeeper(emptyMap(), accounting)

        assertEquals(0L, housekeeper.lastRunTimestamp())
        housekeeper.run()
        assertTrue(housekeeper.lastRunTimestamp() > 0)
    }
}
