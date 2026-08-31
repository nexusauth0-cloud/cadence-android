package com.cadence.core.common

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InBatchesTest {

    @Test
    fun `empty input returns empty list`() = runTest {
        val result = inBatches(emptyList<String>()) { it }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single batch for small input`() = runTest {
        val items = listOf("a", "b", "c")
        val batches = mutableListOf<List<String>>()
        val result = inBatches(items) { batch ->
            batches.add(batch)
            batch.map { it.uppercase() }
        }
        assertEquals(1, batches.size)
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun `sub-batch input within limit`() = runTest {
        val items = (1..100).map { "id$it" }
        val batches = mutableListOf<List<String>>()
        val result = inBatches(items, batchSize = 50) { batch ->
            batches.add(batch)
            batch.map { it }
        }
        assertEquals(2, batches.size)
        assertEquals(100, result.size)
    }

    @Test
    fun `multi-batch input splits correctly`() = runTest {
        val items = (1..2000).map { "id$it" }
        val batches = mutableListOf<List<String>>()
        val result = inBatches(items, batchSize = 900) { batch ->
            batches.add(batch)
            batch.map { it }
        }
        assertEquals(3, batches.size) // 900 + 900 + 200
        assertEquals(2000, result.size)
        assertEquals("id1", result.first())
        assertEquals("id2000", result.last())
    }

    @Test
    fun `dedup preserves first occurrence order`() = runTest {
        val items = listOf("b", "a", "b", "c", "a")
        val result = inBatches(items) { it }
        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `dedup with multi-batch boundary`() = runTest {
        // Put duplicates across batch boundaries
        val items = (1..10).map { "id${it % 3}" } // id1,id2,id0,id1,id2,id0,id1,id2,id0,id1
        val seen = mutableSetOf<String>()
        val result = inBatches(items, batchSize = 3) { batch ->
            // Verify batch size respected
            assertTrue(batch.size <= 3)
            batch.filter { seen.add(it) }
        }
        // First occurrences only, in order
        assertEquals(listOf("id1", "id2", "id0"), result)
    }

    @Test
    fun `all duplicates returns single occurrence`() = runTest {
        val items = listOf("x", "x", "x", "x")
        val result = inBatches(items) { it }
        assertEquals(listOf("x"), result)
    }

    @Test
    fun `batch size of 1 processes each item individually`() = runTest {
        val items = listOf("a", "b", "c")
        val batches = mutableListOf<List<String>>()
        inBatches(items, batchSize = 1) { batch ->
            batches.add(batch)
            batch
        }
        assertEquals(3, batches.size)
        batches.forEach { assertEquals(1, it.size) }
    }

    @Test
    fun `ordering is stable across batches`() = runTest {
        val items = (1..100).map { "item$it" }
        val result = inBatches(items, batchSize = 10) { it }
        assertEquals(items, result)
    }
}
