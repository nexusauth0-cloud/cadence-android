package com.cadence.android.diskcache

import com.cadence.core.cache.CacheBudget
import com.cadence.core.cache.CacheTierId
import com.cadence.core.cache.EvictionPolicy
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileCacheTierStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var store: FileCacheTierStore

    @BeforeEach
    fun setUp() {
        store = FileCacheTierStore(
            tierId = CacheTierId.METADATA,
            rootDir = File(tempDir, "t3"),
            budget = CacheBudget(
                tierId = CacheTierId.METADATA,
                maxBytes = 1024 * 1024,
                evictionPolicy = EvictionPolicy.Ttl(ttlMs = 10_000L),
            ),
        )
    }

    @Test
    fun writeThenReadBytesRoundTrips() = runTest {
        store.write("a", "hello".toByteArray())
        val bytes = store.readBytes("a")
        assertNotNull(bytes)
        assertEquals("hello", String(bytes!!))
    }

    @Test
    fun readMetadataPresents() = runTest {
        val entry = store.write("k", "v".toByteArray())
        val got = store.get("k")
        assertNotNull(got)
        assertEquals(CacheTierId.METADATA, got!!.tierId)
    }

    @Test
    fun absentKeyReturnsNull() = runTest {
        assertNull(store.readBytes("missing"))
        assertNull(store.get("missing"))
    }

    @Test
    fun removeDeletesEntry() = runTest {
        store.write("r", "x".toByteArray())
        assertTrue(store.remove("r"))
        assertNull(store.readBytes("r"))
        assertFalse(store.remove("r"))
    }

    @Test
    fun clearRemovesAll() = runTest {
        store.write("a", "1".toByteArray())
        store.write("b", "2".toByteArray())
        store.clear()
        assertNull(store.readBytes("a"))
        assertNull(store.readBytes("b"))
    }

    @Test
    fun lruEvictionFreesBudget() = runTest {
        val lru = FileCacheTierStore(
            tierId = CacheTierId.STREAM,
            rootDir = File(tempDir, "t2"),
            budget = CacheBudget(
                tierId = CacheTierId.STREAM,
                maxBytes = 30,
                evictionPolicy = EvictionPolicy.Lru(30),
            ),
        )
        lru.write("a", ByteArray(20))
        lru.write("b", ByteArray(20)) // now 40 > 30 → a evicted
        assertNull(lru.readBytes("a"))
        assertNotNull(lru.readBytes("b"))
    }

    @Test
    fun ttlExpiryPrunes() = runTest {
        val ttl = FileCacheTierStore(
            tierId = CacheTierId.METADATA,
            rootDir = File(tempDir, "t3b"),
            budget = CacheBudget(
                tierId = CacheTierId.METADATA,
                maxBytes = 1024 * 1024,
                evictionPolicy = EvictionPolicy.Ttl(ttlMs = -1), // already expired
            ),
        )
        ttl.write("x", "v".toByteArray(), ttlMs = 0)
        assertNull(ttl.readBytes("x"))
    }

    @Test
    fun entryCountAndUsedBytesReflectState() = runTest {
        store.write("a", ByteArray(100))
        store.write("b", ByteArray(150))
        val acc = store.accounting()
        assertEquals(2, acc.entryCount)
        assertEquals(250, acc.usedBytes)
    }
}
