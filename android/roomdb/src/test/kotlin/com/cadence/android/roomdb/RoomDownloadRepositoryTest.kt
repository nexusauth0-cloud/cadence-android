package com.cadence.android.roomdb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadState
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDownloadRepositoryTest {

    private val provider = ProviderId("test")
    private fun ref(id: String) = TrackRef(provider, id)
    private fun meta(title: String, artist: String = "Artist") = DownloadMetadata(title = title, artist = artist)

    private suspend fun buildRepo(): RoomDownloadRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CadenceDatabase::class.java,
        ).build()
        return RoomDownloadRepository.forTesting(db)
    }

    @Test
    fun `enqueue and observeAll`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        assertTrue(id.value > 0)
        val entries = repo.observeAll().first()
        assertEquals(1, entries.size)
        assertEquals(DownloadState.QUEUED, entries[0].state)
        assertEquals("Song 1", entries[0].meta.title)
    }

    @Test
    fun `dedup by ref`() = runTest {
        val repo = buildRepo()
        val id1 = repo.enqueue(ref("t1"), meta("Song 1"))
        val id2 = repo.enqueue(ref("t1"), meta("Song 1 renamed"))
        assertEquals(id1, id2)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `re-download after complete replaces the completed entry`() = runTest {
        val repo = buildRepo()
        val id1 = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.complete(id1, fileSizeBytes = 1000, localUri = "file:///x")
        val id2 = repo.enqueue(ref("t1"), meta("Song 1"))
        assertTrue(id1 != id2)
        // Only 1 entry — old completed was removed to allow re-download.
        assertEquals(1, repo.observeAll().first().size)
        assertEquals(DownloadState.QUEUED, repo.observeAll().first()[0].state)
    }

    @Test
    fun `full lifecycle to completed`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.markDownloading(id, totalBytes = 5000)
        repo.updateProgress(id, progressBytes = 2500, totalBytes = 5000)
        repo.updateProgress(id, progressBytes = 5000, totalBytes = 5000)
        repo.complete(id, fileSizeBytes = 5000, localUri = "file:///downloads/song1")

        val entry = repo.get(id)
        assertNotNull(entry)
        assertEquals(DownloadState.COMPLETED, entry!!.state)
        assertEquals(5000L, entry.fileSizeBytes)
        assertEquals("file:///downloads/song1", entry.localUri)
        assertTrue(entry.isPlayableOffline)
    }

    @Test
    fun `fail and retry`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.NETWORK, "timeout")
        assertEquals(DownloadState.FAILED, repo.get(id)!!.state)

        repo.retry(id)
        assertEquals(DownloadState.QUEUED, repo.get(id)!!.state)
        assertNull(repo.get(id)!!.error)
    }

    @Test
    fun `cancel`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.cancel(id)
        assertEquals(DownloadState.CANCELLED, repo.get(id)!!.state)
    }

    @Test
    fun `remove deletes record`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.remove(id)
        assertNull(repo.get(id))
    }

    @Test
    fun `offlineAvailability for missing ref`() = runTest {
        val repo = buildRepo()
        val avail = repo.offlineAvailability(ref("nonexistent"))
        assertEquals(com.cadence.core.database.OfflineAvailability.NotDownloaded, avail)
    }

    @Test
    fun `offlineAvailability for completed download`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.complete(id, fileSizeBytes = 3000, localUri = "file:///dl")
        val avail = repo.offlineAvailability(ref("t1"))
        assertTrue(avail is com.cadence.core.database.OfflineAvailability.Available)
        assertEquals("file:///dl", (avail as com.cadence.core.database.OfflineAvailability.Available).localUri)
    }

    @Test
    fun `offlineUri returns null for non-completed`() = runTest {
        val repo = buildRepo()
        repo.enqueue(ref("t1"), meta("Song 1"))
        assertNull(repo.offlineUri(ref("t1")))
    }

    @Test
    fun `offlineUri returns localUri for completed`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.complete(id, fileSizeBytes = 1000, localUri = "file:///dl")
        assertEquals("file:///dl", repo.offlineUri(ref("t1")))
    }

    @Test
    fun `storageReport counts only completed`() = runTest {
        val repo = buildRepo()
        val id1 = repo.enqueue(ref("t1"), meta("Song 1"))
        val id2 = repo.enqueue(ref("t2"), meta("Song 2"))
        repo.complete(id1, fileSizeBytes = 3000, localUri = "file:///a")
        repo.markDownloading(id2, totalBytes = 5000)

        val report = repo.storageReport()
        assertEquals(1, report.downloadCount)
        assertEquals(3000L, report.totalBytes)
    }

    @Test
    fun `completedCount and observeCompletedRefs`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.complete(id, fileSizeBytes = 1000, localUri = "file:///a")
        assertEquals(1, repo.completedCount())
        val refs = repo.observeCompletedRefs().first()
        assertEquals(1, refs.size)
        assertEquals(ref("t1"), refs[0])
    }

    @Test
    fun `retry is no-op on completed`() = runTest {
        val repo = buildRepo()
        val id = repo.enqueue(ref("t1"), meta("Song 1"))
        repo.complete(id, fileSizeBytes = 1000, localUri = "file:///a")
        repo.retry(id)
        assertEquals(DownloadState.COMPLETED, repo.get(id)!!.state)
    }

    @Test
    fun `getByRef returns correct entry`() = runTest {
        val repo = buildRepo()
        repo.enqueue(ref("t1"), meta("Song 1"))
        repo.enqueue(ref("t2"), meta("Song 2"))
        assertNotNull(repo.getByRef(ref("t1")))
        assertEquals("Song 1", repo.getByRef(ref("t1"))!!.meta.title)
        assertNull(repo.getByRef(ref("nonexistent")))
    }
}
