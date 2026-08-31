package com.cadence.android.worker

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeStorage(): DownloadStorage = DownloadStorage(
        downloadsDir = File(tmp.newFolder("downloads").absolutePath),
    )

    @Test
    fun `write makes file durable with correct bytes`() = runTest {
        val storage = makeStorage()
        val key = storage.keyFor("provider", "track1")
        val bytes = ByteArray(1024) { it.toByte() }

        val uri = storage.write(key, bytes)

        assertTrue(storage.verify(key, bytes.size.toLong()))
        assertNotNull(uri)
        assertEquals(bytes.toList(), storage.read(key)?.toList())
    }

    @Test
    fun `key is deterministic per provider and native id`() {
        val storage = makeStorage()
        assertEquals(storage.keyFor("a", "b"), storage.keyFor("a", "b"))
        assertTrue(storage.keyFor("a", "b") != storage.keyFor("a", "c"))
        assertTrue(storage.keyFor("a", "b") != storage.keyFor("c", "b"))
    }

    @Test
    fun `verify fails on missing file`() = runTest {
        val storage = makeStorage()
        assertFalse(storage.verify("does-not-exist", 10))
    }

    @Test
    fun `verify fails on mismatched size`() = runTest {
        val storage = makeStorage()
        val key = storage.keyFor("p", "t")
        storage.write(key, ByteArray(100))
        assertTrue(storage.verify(key, 100))
        assertFalse(storage.verify(key, 200))
    }

    @Test
    fun `delete removes file and underlying bytes`() = runTest {
        val storage = makeStorage()
        val key = storage.keyFor("p", "t")
        storage.write(key, ByteArray(50))
        assertTrue(storage.verify(key))

        assertTrue(storage.delete(key))
        assertFalse(storage.verify(key))
        assertNull(storage.read(key))
    }

    @Test
    fun `totalBytes reflects written content only`() = runTest {
        val storage = makeStorage()
        storage.write(storage.keyFor("p", "a"), ByteArray(100))
        storage.write(storage.keyFor("p", "b"), ByteArray(50))
        assertEquals(150, storage.totalBytes())
    }
}
