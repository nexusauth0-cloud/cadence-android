package com.cadence.android.roomdb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cadence.core.database.AddedReason
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device (instrumented) persistence test. The JVM unit tests use an
 * in-memory Room database; this test opens the real on-disk file via
 * [RoomPlaylistRepository.open] and proves rows survive a fresh reopen — the
 * storage-contract guarantee that in-memory tests cannot establish.
 */
@RunWith(AndroidJUnit4::class)
class RoomPlaylistRepositoryInstrumentedTest {

    private val context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun ref(nativeId: String) =
        TrackRef(providerId = ProviderId("demo"), nativeId = nativeId)

    @Test
    fun rowsPersistAcrossReopen() = runTest {
        val id = open().let { first ->
            val created = first.create("Persisted", "survives reopen")
            first.addTracks(created, listOf(ref("t-1"), ref("t-2")), AddedReason.USER)
            created
        }

        // Reopen from the same on-disk file and verify the data is intact.
        val second = open()
        val metadata = second.getMetadata(id)
        assertNotNull(metadata)
        assertEquals("Persisted", metadata!!.title)
        assertEquals(2, metadata.trackCount)
        assertEquals(listOf("t-1", "t-2"), second.getTracks(id).map { it.ref.nativeId })
    }

    private fun open(): RoomPlaylistRepository = RoomPlaylistRepository.open(context)
}
