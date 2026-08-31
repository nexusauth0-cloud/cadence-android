package com.cadence.app.viewmodel

import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.InMemoryDownloadRepository
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.domain.CancelDownloadUseCase
import com.cadence.core.domain.DownloadOperationsPort
import com.cadence.core.domain.DownloadStorageUseCase
import com.cadence.core.domain.DownloadStatusUseCase
import com.cadence.core.domain.ObserveDownloadsUseCase
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: InMemoryDownloadRepository
    private lateinit var operations: FakeOperations

    private val ref = TrackRef(ProviderId("demo"), "t-a")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = InMemoryDownloadRepository()
        operations = FakeOperations(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): DownloadsViewModel = DownloadsViewModel(
        observeDownloads = ObserveDownloadsUseCase(repo),
        downloadStorage = DownloadStorageUseCase(repo),
        cancelDownload = CancelDownloadUseCase(repo),
        downloadOperations = operations,
    )

    private suspend fun seedCompleted() {
        val id = repo.enqueue(ref, DownloadMetadata(title = "Alpha"))
        repo.complete(id, fileSizeBytes = 2048, localUri = "file:///x.mp3")
    }

    @Test
    fun `availability reports VerifiedOffline only after completion`() = runTest {
        val viewModel = vm()
        val av = viewModel.availabilityForKey(ref)

        assertEquals(OfflineAvailability.NotDownloaded, av.value)

        repo.enqueue(ref, DownloadMetadata(title = "Alpha"))
        repo.markDownloading(
            com.cadence.core.database.DownloadId(repo.getByRef(ref)!!.id.value),
            totalBytes = 4000,
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<OfflineAvailability.Downloading>(av.value)

        seedCompleted()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<OfflineAvailability.Available>(av.value)
    }

    @Test
    fun `download delegates to operations port`() = runTest {
        val viewModel = vm()
        val track = Track(ref = ref, title = "Alpha")
        viewModel.download(track)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, operations.downloadCalls)
    }

    @Test
    fun `storage report reflects completed downloads`() = runTest {
        seedCompleted()
        val viewModel = vm()
        viewModel.refreshStorage()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.storage.value?.downloadCount)
    }

    private class FakeOperations(
        private val repo: InMemoryDownloadRepository,
    ) : DownloadOperationsPort {
        var downloadCalls = 0
        override suspend fun download(track: Track): Long {
            downloadCalls++
            return repo.enqueue(track.ref, DownloadMetadata(title = track.title)).value
        }

        override suspend fun cancel(downloadId: Long) {
            repo.cancel(DownloadId(downloadId))
        }

        override suspend fun remove(downloadId: Long) {
            repo.remove(DownloadId(downloadId))
        }

        override suspend fun retry(downloadId: Long) {
            repo.retry(DownloadId(downloadId))
        }

        override suspend fun downloadAll(tracks: List<Track>): Int {
            var n = 0
            tracks.forEach { n += download(it).let { 1 } }
            return n
        }
    }
}
