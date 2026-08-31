package com.cadence.android.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.cadence.android.diskcache.FileCacheTierStore
import com.cadence.core.cache.CacheBudget
import com.cadence.core.cache.CacheTierId
import com.cadence.core.cache.EvictionPolicy
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheHousekeepingWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var tierRoot: File
    private lateinit var backupRoot: File
    private lateinit var sessionStore: FileCacheTierStore

    @Before
    fun setUp() {
        tierRoot = File(context.cacheDir, "test_tier")
        backupRoot = File(context.filesDir, "test_backups")
        tierRoot.deleteRecursively()
        backupRoot.deleteRecursively()
        sessionStore = FileCacheTierStore(
            tierId = CacheTierId.STREAM,
            rootDir = File(tierRoot, "stream"),
            budget = CacheBudget(
                tierId = CacheTierId.STREAM,
                maxBytes = 30,
                evictionPolicy = EvictionPolicy.Lru(30),
            ),
        )
    }

    @After
    fun tearDown() {
        tierRoot.deleteRecursively()
        backupRoot.deleteRecursively()
    }

    private class InjectedWorker(
        context: Context,
        params: WorkerParameters,
        private val injectedTiers: Map<CacheTierId, FileCacheTierStore>,
        private val injectedBackup: File,
    ) : CacheHousekeepingWorker(context, params) {
        override fun tiers(): Map<CacheTierId, FileCacheTierStore> = injectedTiers
        override fun backupDirectory(): File = injectedBackup
    }

    private fun buildInjected(): InjectedWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker =
                InjectedWorker(appContext, workerParameters, mapOf(CacheTierId.STREAM to sessionStore), backupRoot)
        }
        return TestListenableWorkerBuilder<CacheHousekeepingWorker>(context)
            .setWorkerFactory(factory)
            .build() as InjectedWorker
    }

    @Test
    fun workerCompletesSuccessWithNoTiers() = runTest {
        val worker = TestListenableWorkerBuilder<CacheHousekeepingWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerCapsBackupFolderToThreeCopies() = runTest {
        backupRoot.mkdirs()
        repeat(6) { i ->
            File(backupRoot, "backup-$i.db").apply {
                writeText("x")
                setLastModified(System.currentTimeMillis() + i * 1000L)
            }
        }

        val worker = buildInjected()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(3, backupRoot.listFiles()?.size ?: 0)
    }

    @Test
    fun workerPrunesOverBudgetTier() = runTest {
        sessionStore.write("big-a", ByteArray(20))
        sessionStore.write("big-b", ByteArray(20)) // 40 > 30 → LRU eviction by housekeeper

        val worker = buildInjected()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val remaining = sessionStore.entries().size
        assertTrue(remaining <= 1)
    }
}
