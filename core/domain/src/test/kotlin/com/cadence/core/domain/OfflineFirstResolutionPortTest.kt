package com.cadence.core.domain

import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.InMemoryDownloadRepository
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfflineFirstResolutionPortTest {

    private val provider = ProviderId("demo")
    private val ref = TrackRef(provider, "alpha-track")
    private val remoteSource = ResolvedSource(
        ref = ref,
        playbackUri = "https://example.com/stream.mp3",
        resolutionId = "remote",
        metadata = SourceMetadata(sizeBytes = 5000),
    )

    private class FakeRemote(
        private val source: ResolvedSource,
    ) : SourceResolutionPort {
        var resolveCalls = 0
        override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource {
            resolveCalls++
            return source
        }
    }

    private suspend fun completed(repo: InMemoryDownloadRepository, uri: String = "file:///dl.mp3") {
        val id = repo.enqueue(ref, com.cadence.core.database.DownloadMetadata(title = "Alpha"))
        repo.complete(id, fileSizeBytes = 5000, localUri = uri)
    }

    @Test
    fun `downloaded track prefers local source`() = runTest {
        val repo = InMemoryDownloadRepository()
        completed(repo)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        val result = port.resolve(ref, ResolutionHints())

        assertEquals("file:///dl.mp3", result.playbackUri)
        assertEquals("offline", result.renditionLabel)
        assertEquals(0, remote.resolveCalls) // never contacted remote
    }

    @Test
    fun `non-downloaded track resolves remotely in normal mode`() = runTest {
        val repo = InMemoryDownloadRepository()
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        val result = port.resolve(ref, ResolutionHints())

        assertEquals("https://example.com/stream.mp3", result.playbackUri)
        assertEquals(1, remote.resolveCalls)
    }

    @Test
    fun `non-downloaded track with offlineOnly is honestly unavailable without network`() = runTest {
        val repo = InMemoryDownloadRepository()
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        assertFailsWith<SourceErrorUnavailable> {
            port.resolve(ref, ResolutionHints(offlineOnly = true))
        }
        assertEquals(0, remote.resolveCalls)
    }

    @Test
    fun `downloaded track with offlineOnly prefers local without network`() = runTest {
        val repo = InMemoryDownloadRepository()
        completed(repo)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        val result = port.resolve(ref, ResolutionHints(offlineOnly = true))
        assertEquals("file:///dl.mp3", result.playbackUri)
        assertEquals(0, remote.resolveCalls)
    }

    @Test
    fun `failed download falls back to remote in normal mode`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(ref, com.cadence.core.database.DownloadMetadata(title = "Alpha"))
        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.NETWORK, null)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        val result = port.resolve(ref, ResolutionHints())
        assertEquals("https://example.com/stream.mp3", result.playbackUri)
    }

    @Test
    fun `failed download with offlineOnly is honestly unavailable`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(ref, com.cadence.core.database.DownloadMetadata(title = "Alpha"))
        repo.markDownloading(id, totalBytes = null)
        repo.fail(id, DownloadFailureReason.NETWORK, null)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        assertFailsWith<SourceErrorUnavailable> {
            port.resolve(ref, ResolutionHints(offlineOnly = true))
        }
    }

    @Test
    fun `downloading track resolves remotely in normal mode`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(ref, com.cadence.core.database.DownloadMetadata(title = "Alpha"))
        repo.markDownloading(id, totalBytes = 8000)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        val result = port.resolve(ref, ResolutionHints())
        assertEquals("https://example.com/stream.mp3", result.playbackUri)
    }

    @Test
    fun `downloading track with offlineOnly is not yet available`() = runTest {
        val repo = InMemoryDownloadRepository()
        val id = repo.enqueue(ref, com.cadence.core.database.DownloadMetadata(title = "Alpha"))
        repo.markDownloading(id, totalBytes = 8000)
        val remote = FakeRemote(remoteSource)
        val port = OfflineFirstResolutionPort(repo, remote)

        assertFailsWith<SourceErrorUnavailable> {
            port.resolve(ref, ResolutionHints(offlineOnly = true))
        }
        assertEquals(0, remote.resolveCalls)
    }
}
