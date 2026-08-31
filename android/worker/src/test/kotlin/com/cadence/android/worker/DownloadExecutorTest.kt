package com.cadence.android.worker

import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.DownloadState
import com.cadence.core.database.InMemoryDownloadRepository
import com.cadence.core.domain.SourceResolutionPort
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadExecutorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val provider = ProviderId("demo")
    private val ref = TrackRef(provider, "track-a")
    private val bytes: ByteArray = ByteArray(2048) { it.toByte() }

    private fun repo(): DownloadRepository {
        val r = InMemoryDownloadRepository()
        // warm any internal structures
        return r
    }

    private fun storage(): DownloadStorage = DownloadStorage(
        downloadsDir = File(tmp.newFolder("dl_${hashCode()}").absolutePath),
    )

    private class FakeByteSource(
        private val data: ByteArray,
        private val totalBytes: Long?,
        private val fail: Boolean = false,
    ) : HttpByteSource() {
        override fun open(uri: String, headers: Map<String, String>): StreamInfo {
            if (fail) throw java.io.IOException("network down")
            return StreamInfo(stream = BytesStream(data), totalBytes = totalBytes)
        }
    }

    private class BytesStream(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) (data[pos++].toInt() and 0xFF) else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    private fun alwaysResolution(size: Long?): SourceResolutionPort = object : SourceResolutionPort {
        override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource =
            ResolvedSource(ref, "https://example.com/x.mp3", "r", metadata = SourceMetadata(sizeBytes = size))
    }

    private fun executor(
        repo: DownloadRepository,
        store: DownloadStorage,
        size: Long?,
        streamTotal: Long? = size,
        fail: Boolean = false,
    ) = DownloadExecutor(
        repository = repo,
        resolution = alwaysResolution(size),
        storage = store,
        byteSource = FakeByteSource(bytes, streamTotal, fail),
    )

    @Test
    fun `completed download persists VerifiedOffline state with local uri`() = runTest {
        val r = repo()
        val s = storage()
        val id = r.enqueue(ref, DownloadMetadata(title = "A"))
        val outcome = executor(r, s, size = 2048L).run(id, provider.value, ref.nativeId) { false }

        assertEquals(DownloadExecutor.Outcome.Completed, outcome)
        val e = r.get(id)!!
        assertEquals(DownloadState.COMPLETED, e.state)
        assertTrue(e.isPlayableOffline)
        assertEquals(2048L, e.fileSizeBytes)
        assertTrue(e.localUri!!.startsWith("file://"))
    }

    @Test
    fun `network failure marks failed without leaving an asset`() = runTest {
        val r = repo()
        val s = storage()
        val id = r.enqueue(ref, DownloadMetadata(title = "A"))
        val outcome = executor(r, s, size = 2048L, fail = true).run(id, provider.value, ref.nativeId) { false }

        assertEquals(DownloadExecutor.Outcome.Retriable, outcome)
        val e = r.get(id)!!
        assertEquals(DownloadState.FAILED, e.state)
        assertTrue(!e.isPlayableOffline)
        assertTrue(!s.verify(s.keyFor(provider.value, ref.nativeId)))
    }

    @Test
    fun `integrity mismatch does not leave false asset`() = runTest {
        val r = repo()
        val s = storage()
        val id = r.enqueue(ref, DownloadMetadata(title = "A"))
        // Stream has no known size, resolution claims 99_999 -> stored 2048 != expected.
        val outcome = executor(r, s, size = 99_999L, streamTotal = null).run(id, provider.value, ref.nativeId) { false }

        assertEquals(DownloadExecutor.Outcome.Retriable, outcome)
        assertEquals(DownloadState.FAILED, r.get(id)!!.state)
        assertTrue(!s.verify(s.keyFor(provider.value, ref.nativeId)))
    }

    @Test
    fun `cancellation mid-stream cancels record and removes asset`() = runTest {
        val r = repo()
        val s = storage()
        val id = r.enqueue(ref, DownloadMetadata(title = "A"))
        // Cancel after first chunk.
        var calls = 0
        val outcome = executor(r, s, size = 2048L).run(id, provider.value, ref.nativeId) {
            calls++
            calls >= 2 // stop on the second evaluation (after starting)
        }

        assertEquals(DownloadExecutor.Outcome.Completed, outcome)
        val e = r.get(id)!!
        assertEquals(DownloadState.CANCELLED, e.state)
        assertTrue(!s.verify(s.keyFor(provider.value, ref.nativeId)))
    }
}
