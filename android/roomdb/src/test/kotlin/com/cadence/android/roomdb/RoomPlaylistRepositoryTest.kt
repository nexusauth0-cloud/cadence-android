package com.cadence.android.roomdb

import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.core.database.AddedReason
import com.cadence.core.database.PlaylistId
import com.cadence.core.database.PlaylistRepository
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomPlaylistRepositoryTest {

    private lateinit var database: CadenceDatabase
    private lateinit var repository: PlaylistRepository

    private fun ref(provider: String = "demo", id: String): TrackRef =
        TrackRef(providerId = ProviderId(provider), nativeId = id)

    @Before
    fun setUp() {
        database = CadenceDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = RoomPlaylistRepository.forTesting(
            database = database,
            dispatcher = Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createThenGetMetadata() = runTest {
        val id = repository.create("My Playlist", "desc")
        val metadata = repository.getMetadata(id)
        assertNotNull(metadata)
        assertEquals("My Playlist", metadata!!.title)
        assertEquals("desc", metadata.description)
        assertEquals(0, metadata.trackCount)
    }

    @Test
    fun updateMetadata() = runTest {
        val id = repository.create("Old")
        repository.updateMetadata(id, "New", "new desc")
        val metadata = repository.getMetadata(id)
        assertEquals("New", metadata!!.title)
        assertEquals("new desc", metadata.description)
    }

    @Test
    fun deleteRemovesPlaylistAndTracks() = runTest {
        val id = repository.create("p", "d")
        repository.addTrack(id, ref(id = "track-1"))
        assertTrue(repository.delete(id))
        assertNull(repository.getMetadata(id))
        // adding to a deleted playlist must fail loudly
        assertFalse(repository.delete(id))
    }

    @Test
    fun addTracksPreservesOrder() = runTest {
        val id = repository.create("p")
        repository.addTracks(
            id,
            listOf(ref(id = "a"), ref(id = "b"), ref(id = "c")),
        )
        val tracks = repository.getTracks(id)
        assertEquals(3, tracks.size)
        assertEquals(
            listOf("a", "b", "c"),
            tracks.map { it.ref.nativeId },
        )
        assertEquals(listOf(0, 1, 2), tracks.map { it.position })
        assertEquals(3, repository.trackCount(id))
    }

    @Test
    fun addTrackWithReason() = runTest {
        val id = repository.create("p")
        repository.addTrack(id, ref(id = "x"), reason = AddedReason.RADIO)
        val track = repository.getTracks(id).single()
        assertEquals(AddedReason.RADIO, track.addedReason)
    }

    @Test
    fun removeTrackRemovesAllOccurrences() = runTest {
        val id = repository.create("p")
        val theRef = ref(id = "dup")
        repository.addTracks(id, listOf(theRef, ref(id = "other"), theRef))
        val removed = repository.removeTrack(id, theRef)
        assertEquals(2, removed)
        assertEquals(listOf("other"), repository.getTracks(id).map { it.ref.nativeId })
    }

    @Test
    fun removeAtPositionsReindexes() = runTest {
        val id = repository.create("p")
        repository.addTracks(id, listOf(ref(id = "a"), ref(id = "b"), ref(id = "c"), ref(id = "d")))
        val removed = repository.removeAtPositions(id, listOf(0, 2))
        assertEquals(2, removed)
        val tracks = repository.getTracks(id)
        assertEquals(listOf("b", "d"), tracks.map { it.ref.nativeId })
        assertEquals(listOf(0, 1), tracks.map { it.position })
    }

    @Test
    fun reorderMovesAndReindexes() = runTest {
        val id = repository.create("p")
        repository.addTracks(id, listOf(ref(id = "a"), ref(id = "b"), ref(id = "c")))

        assertTrue(repository.reorder(id, 0, 2))
        assertEquals(listOf("b", "c", "a"), repository.getTracks(id).map { it.ref.nativeId })

        assertFalse(repository.reorder(id, 0, 99))
    }

    @Test
    fun replaceTracksIsAtomic() = runTest {
        val id = repository.create("p")
        repository.addTracks(id, listOf(ref(id = "a"), ref(id = "b")))
        repository.replaceTracks(
            id,
            listOf(
                com.cadence.core.database.PlaylistTrack(ref(id = "x"), position = 0),
                com.cadence.core.database.PlaylistTrack(ref(id = "y"), position = 5),
            ),
        )
        val tracks = repository.getTracks(id)
        assertEquals(listOf("x", "y"), tracks.map { it.ref.nativeId })
        assertEquals(listOf(0, 1), tracks.map { it.position })
    }

    @Test
    fun checkDuplicatesFindsPositions() = runTest {
        val id = repository.create("p")
        repository.addTracks(id, listOf(ref(id = "a"), ref(id = "b"), ref(id = "a")))

        val results = repository.checkDuplicates(
            id,
            listOf(ref(id = "a"), ref(id = "missing"), ref(id = "b")),
        )
        assertEquals(listOf(0, 2), results[0].existingPositions)
        assertTrue(results[1].existingPositions.isEmpty())
        assertEquals(listOf(1), results[2].existingPositions)
    }

    @Test
    fun listAllReturnsMetadataWithCounts() = runTest {
        val a = repository.create("a")
        val b = repository.create("b")
        repository.addTrack(a, ref(id = "t1"))
        repository.addTrack(a, ref(id = "t2"))

        val all = repository.listAll()
        assertEquals(2, all.size)
        val metaA = all.first { it.id == a }
        assertEquals(2, metaA.trackCount)
        val metaB = all.first { it.id == b }
        assertEquals(0, metaB.trackCount)
    }

    @Test
    fun unknownPlaylistOpsFailLoudly() = runTest {
        val missing = PlaylistId("nope")
        val thrown = runCatching { repository.getTracks(missing) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)

        val thrownAdd = runCatching { repository.addTrack(missing, ref(id = "x")) }.exceptionOrNull()
        assertTrue(thrownAdd is IllegalArgumentException)
    }
}