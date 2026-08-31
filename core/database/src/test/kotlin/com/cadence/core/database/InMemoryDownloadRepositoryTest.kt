package com.cadence.core.database

import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryDownloadRepositoryTest {

    private val provider = ProviderId("test")
    private fun ref(native: String) = TrackRef(provider, native)
    private fun meta(title: String, artist: String = "Artist") = DownloadMetadata(title = title, artist = artist)

    private val alphaRef = ref("alpha-track")
    private val bravoRef = ref("bravo-track")
    private val alphaMeta = meta("Alpha Song", "Alpha Artist")
    private val bravoMeta = meta("Bravo Song", "Bravo Artist")

    @Test
    fun `enqueue creates a QUEUED entry and observeAll emits it`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)

        assertTrue(id.value > 0)
        val entries = repo.observeAll().first()
        assertEquals(1, entries.size)
        assertEquals(DownloadState.QUEUED, entries[0].state)
        assertEquals(alphaRef, entries[0].ref)
        assertEquals("Alpha Song", entries[0].meta.title)
    }

    @Test
    fun `enqueue deduplicates for the same TrackRef`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id1 = repo.enqueue(alphaRef, alphaMeta)
        val id2 = repo.enqueue(alphaRef, meta("Different Title"))

        assertEquals(id1, id2)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `different refs get different ids`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id1 = repo.enqueue(alphaRef, alphaMeta)
        val id2 = repo.enqueue(bravoRef, bravoMeta)
        assertTrue(id1 != id2)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `full lifecycle enqueue through downloading progress to complete`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)

        repo.markDownloading(id, totalBytes = 1000)
        var entry = repo.get(id)
        assertEquals(DownloadState.DOWNLOADING, entry?.state)
        assertEquals(1000L, entry?.totalBytes)

        repo.updateProgress(id, progressBytes = 500, totalBytes = 1000)
        entry = repo.get(id)
        assertEquals(500L, entry?.progressBytes)

        repo.complete(id, fileSizeBytes = 1000, localUri = "file:///downloads/alpha")
        entry = repo.get(id)
        assertEquals(DownloadState.COMPLETED, entry?.state)
        assertEquals(1000L, entry?.fileSizeBytes)
        assertEquals("file:///downloads/alpha", entry?.localUri)
        assertTrue(entry?.isPlayableOffline == true)
    }

    @Test
    fun `fail marks entry with reason and message`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)

        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.NETWORK, "Connection lost")

        val entry = repo.get(id)
        assertEquals(DownloadState.FAILED, entry?.state)
        assertEquals(DownloadFailureReason.NETWORK, entry?.error)
        assertEquals("Connection lost", entry?.errorMessage)
        assertFalse(entry?.isPlayableOffline == true)
    }

    @Test
    fun `cancel transitions to CANCELLED`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.cancel(id)

        val entry = repo.get(id)
        assertEquals(DownloadState.CANCELLED, entry?.state)
        assertFalse(entry?.isPlayableOffline == true)
    }

    @Test
    fun `retry resets FAILED entry to QUEUED`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.STORAGE, "Disk full")

        repo.retry(id)
        val entry = repo.get(id)
        assertEquals(DownloadState.QUEUED, entry?.state)
        assertEquals(0L, entry?.progressBytes)
        assertNull(entry?.error)
        assertNull(entry?.localUri)
    }

    @Test
    fun `retry on COMPLETED entry is a no-op`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.complete(id, fileSizeBytes = 500, localUri = "file:///x")

        repo.retry(id)
        val entry = repo.get(id)
        assertEquals(DownloadState.COMPLETED, entry?.state)
    }

    @Test
    fun `remove deletes the record`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.remove(id)

        assertNull(repo.get(id))
        assertEquals(0, repo.observeAll().first().size)
    }

    @Test
    fun `getByRef finds entry for a track`() = runTest {
        val repo = InMemoryDownloadRepository()
        assertNull(repo.getByRef(alphaRef))

        repo.enqueue(alphaRef, alphaMeta)
        assertNotNull(repo.getByRef(alphaRef))
    }

    @Test
    fun `offlineAvailability returns NotDownloaded when no entry`() = runTest {
        val repo = InMemoryDownloadRepository()
        assertEquals(OfflineAvailability.NotDownloaded, repo.offlineAvailability(alphaRef))
    }

    @Test
    fun `offlineAvailability returns Available for completed download`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.complete(id, fileSizeBytes = 2000, localUri = "file:///alpha")

        val avail = repo.offlineAvailability(alphaRef)
        assertTrue(avail is OfflineAvailability.Available)
        assertEquals("file:///alpha", (avail as OfflineAvailability.Available).localUri)
        assertEquals(2000L, avail.fileSizeBytes)
    }

    @Test
    fun `offlineAvailability returns Downloading for in-progress download`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.markDownloading(id, totalBytes = 5000)
        repo.updateProgress(id, progressBytes = 2500, totalBytes = 5000)

        val avail = repo.offlineAvailability(alphaRef)
        assertTrue(avail is OfflineAvailability.Downloading)
        assertEquals(2500L, (avail as OfflineAvailability.Downloading).progressBytes)
        assertEquals(5000L, avail.totalBytes)
    }

    @Test
    fun `offlineAvailability returns Failed for failed download`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.NETWORK, null)

        val avail = repo.offlineAvailability(alphaRef)
        assertTrue(avail is OfflineAvailability.Failed)
        assertEquals(DownloadFailureReason.NETWORK, (avail as OfflineAvailability.Failed).reason)
    }

    @Test
    fun `offlineUri returns null for non-completed entry`() = runTest {
        val repo = InMemoryDownloadRepository()
        repo.enqueue(alphaRef, alphaMeta)
        assertNull(repo.offlineUri(alphaRef))
    }

    @Test
    fun `offlineUri returns localUri for completed entry`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        repo.complete(id, fileSizeBytes = 1000, localUri = "file:///alpha.mp3")
        assertEquals("file:///alpha.mp3", repo.offlineUri(alphaRef))
    }

    @Test
    fun `storageReport counts only completed downloads`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id1 = repo.enqueue(alphaRef, alphaMeta)
        val id2 = repo.enqueue(bravoRef, bravoMeta)
        repo.complete(id1, fileSizeBytes = 3000, localUri = "file:///a")
        repo.markDownloading(id2, totalBytes = 5000)

        val report = repo.storageReport()
        assertEquals(1, report.downloadCount)
        assertEquals(3000L, report.totalBytes)
    }

    @Test
    fun `completedCount and observeCompletedRefs`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id1 = repo.enqueue(alphaRef, alphaMeta)
        val id2 = repo.enqueue(bravoRef, bravoMeta)
        repo.complete(id1, fileSizeBytes = 1000, localUri = "file:///a")

        assertEquals(1, repo.completedCount())
        val refs = repo.observeCompletedRefs().first()
        assertEquals(1, refs.size)
        assertEquals(alphaRef, refs[0])
    }

    @Test
    fun `enqueue completed download again replaces the completed entry`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id1 = repo.enqueue(alphaRef, alphaMeta)
        repo.complete(id1, fileSizeBytes = 1000, localUri = "file:///a")

        // Re-enqueueing a completed download removes the old entry and creates a new one.
        val id2 = repo.enqueue(alphaRef, alphaMeta)
        assertTrue(id1 != id2)
        // Only 1 entry exists — the old completed one was removed.
        assertEquals(1, repo.observeAll().first().size)
        assertEquals(DownloadState.QUEUED, repo.observeAll().first()[0].state)
    }

    @Test
    fun `observe emits on every state change`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(alphaRef, alphaMeta)
        val snapshots = mutableListOf<List<DownloadEntry>>()
        // Collect into snapshots (we use first() to peek)
        snapshots.add(repo.observeAll().first())
        assertEquals(DownloadState.QUEUED, snapshots.last().first().state)

        repo.markDownloading(id, totalBytes = 100)
        snapshots.add(repo.observeAll().first())
        assertEquals(DownloadState.DOWNLOADING, snapshots.last().first().state)

        repo.complete(id, fileSizeBytes = 100, localUri = "file:///x")
        snapshots.add(repo.observeAll().first())
        assertEquals(DownloadState.COMPLETED, snapshots.last().first().state)
    }
}
