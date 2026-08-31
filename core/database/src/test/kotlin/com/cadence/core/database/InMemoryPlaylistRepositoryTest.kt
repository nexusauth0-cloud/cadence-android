package com.cadence.core.database

import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPlaylistRepositoryTest {

    private val repo = InMemoryPlaylistRepository()
    private val DEMO = ProviderId("demo")

    private fun ref(id: String) = TrackRef(DEMO, id)

    @Test
    fun `create and read metadata`() = runTest {
        val id = repo.create("My Playlist", "A test playlist")
        val meta = repo.getMetadata(id)
        assertNotNull(meta)
        assertEquals("My Playlist", meta.title)
        assertEquals("A test playlist", meta.description)
        assertEquals(0, meta.trackCount)
    }

    @Test
    fun `update metadata`() = runTest {
        val id = repo.create("Old Title")
        repo.updateMetadata(id, "New Title", "Updated")
        val meta = repo.getMetadata(id)
        assertEquals("New Title", meta?.title)
        assertEquals("Updated", meta?.description)
    }

    @Test
    fun `delete playlist`() = runTest {
        val id = repo.create("Doomed")
        assertTrue(repo.delete(id))
        assertNull(repo.getMetadata(id))
    }

    @Test
    fun `delete returns false for missing`() = runTest {
        assertFalse(repo.delete(PlaylistId("nonexistent")))
    }

    @Test
    fun `add tracks and read in order`() = runTest {
        val id = repo.create("Ordered")
        repo.addTrack(id, ref("a"))
        repo.addTrack(id, ref("b"))
        repo.addTrack(id, ref("c"))
        val tracks = repo.getTracks(id)
        assertEquals(3, tracks.size)
        assertEquals("a", tracks[0].ref.nativeId)
        assertEquals("b", tracks[1].ref.nativeId)
        assertEquals("c", tracks[2].ref.nativeId)
        assertEquals(0, tracks[0].position)
        assertEquals(1, tracks[1].position)
        assertEquals(2, tracks[2].position)
    }

    @Test
    fun `addTracks preserves order`() = runTest {
        val id = repo.create("Batch")
        repo.addTracks(id, listOf(ref("x"), ref("y"), ref("z")))
        val tracks = repo.getTracks(id)
        assertEquals(listOf("x", "y", "z"), tracks.map { it.ref.nativeId })
    }

    @Test
    fun `removeTrack removes all occurrences`() = runTest {
        val id = repo.create("Dedup")
        repo.addTrack(id, ref("a"))
        repo.addTrack(id, ref("b"))
        repo.addTrack(id, ref("a"))
        val removed = repo.removeTrack(id, ref("a"))
        assertEquals(1, removed) // InMemory returns 1 if any removed
        val tracks = repo.getTracks(id)
        assertEquals(1, tracks.size)
        assertEquals("b", tracks[0].ref.nativeId)
    }

    @Test
    fun `reorder moves track`() = runTest {
        val id = repo.create("Reorder")
        repo.addTracks(id, listOf(ref("a"), ref("b"), ref("c")))
        assertTrue(repo.reorder(id, 0, 2)) // move first to last
        val tracks = repo.getTracks(id)
        assertEquals(listOf("b", "c", "a"), tracks.map { it.ref.nativeId })
        // Positions re-indexed
        assertEquals(listOf(0, 1, 2), tracks.map { it.position })
    }

    @Test
    fun `reorder returns false for out-of-range`() = runTest {
        val id = repo.create("Small")
        repo.addTrack(id, ref("a"))
        assertFalse(repo.reorder(id, 0, 5))
    }

    @Test
    fun `replaceTracks atomically`() = runTest {
        val id = repo.create("Replace")
        repo.addTracks(id, listOf(ref("old1"), ref("old2")))
        repo.replaceTracks(id, listOf(
            PlaylistTrack(ref("new1"), 0),
            PlaylistTrack(ref("new2"), 1),
            PlaylistTrack(ref("new3"), 2),
        ))
        val tracks = repo.getTracks(id)
        assertEquals(3, tracks.size)
        assertEquals(listOf("new1", "new2", "new3"), tracks.map { it.ref.nativeId })
    }

    @Test
    fun `checkDuplicates finds existing positions`() = runTest {
        val id = repo.create("DupCheck")
        repo.addTracks(id, listOf(ref("a"), ref("b"), ref("a"), ref("c")))
        val results = repo.checkDuplicates(id, listOf(ref("a"), ref("d"), ref("c")))
        assertEquals(3, results.size)
        assertEquals(listOf(0, 2), results[0].existingPositions) // ref "a"
        assertTrue(results[1].existingPositions.isEmpty()) // ref "d"
        assertEquals(listOf(3), results[2].existingPositions) // ref "c"
    }

    @Test
    fun `trackCount is accurate`() = runTest {
        val id = repo.create("Count")
        assertEquals(0, repo.trackCount(id))
        repo.addTrack(id, ref("a"))
        assertEquals(1, repo.trackCount(id))
        repo.addTrack(id, ref("b"))
        assertEquals(2, repo.trackCount(id))
        repo.removeTrack(id, ref("a"))
        assertEquals(1, repo.trackCount(id))
    }

    @Test
    fun `listAll returns all playlists`() = runTest {
        repo.create("P1")
        repo.create("P2")
        repo.create("P3")
        val all = repo.listAll()
        assertEquals(3, all.size)
        assertEquals(setOf("P1", "P2", "P3"), all.map { it.title }.toSet())
    }

    @Test
    fun `transactional replaceTracks is atomic`() = runTest {
        val id = repo.create("Atomic")
        repo.addTracks(id, listOf(ref("a"), ref("b"), ref("c")))
        // Replace with new list
        repo.replaceTracks(id, listOf(PlaylistTrack(ref("z"), 0)))
        val tracks = repo.getTracks(id)
        assertEquals(1, tracks.size)
        assertEquals("z", tracks[0].ref.nativeId)
    }

    @Test
    fun `1001 IDs through addTracks`() = runTest {
        val id = repo.create("BigList")
        val refs = (1..1001).map { ref("track$it") }
        repo.addTracks(id, refs)
        assertEquals(1001, repo.trackCount(id))
        val tracks = repo.getTracks(id)
        assertEquals(1001, tracks.size)
        assertEquals("track1", tracks.first().ref.nativeId)
        assertEquals("track1001", tracks.last().ref.nativeId)
    }

    @Test
    fun `1200-ID duplicate check`() = runTest {
        val id = repo.create("BigDup")
        val refs = (1..1200).map { ref("t$it") }
        repo.addTracks(id, refs)
        // Check all 1200 + 5 non-existent
        val checkRefs = refs + listOf(ref("missing1"), ref("missing2"), ref("missing3"), ref("missing4"), ref("missing5"))
        val results = repo.checkDuplicates(id, checkRefs)
        assertEquals(1205, results.size)
        // All original refs should have exactly one position
        results.take(1200).forEachIndexed { i, result ->
            assertEquals(1, result.existingPositions.size, "ref t${i+1} should appear once")
        }
        // Missing refs should have empty positions
        results.drop(1200).forEach { result ->
            assertTrue(result.existingPositions.isEmpty())
        }
    }

    @Test
    fun `empty operations are safe`() = runTest {
        val id = repo.create("Empty")
        repo.addTracks(id, emptyList())
        assertEquals(0, repo.trackCount(id))
        val removed = repo.removeTrack(id, ref("ghost"))
        assertEquals(0, removed)
    }

    @Test
    fun `repeated IDs in addTracks`() = runTest {
        val id = repo.create("Repeat")
        repo.addTracks(id, listOf(ref("a"), ref("a"), ref("a")))
        assertEquals(3, repo.trackCount(id))
        val tracks = repo.getTracks(id)
        assertEquals(3, tracks.size) // all three added, no dedup at add time
    }

    @Test
    fun `removeAtPositions sorts descending internally`() = runTest {
        val id = repo.create("RemovePos")
        repo.addTracks(id, listOf(ref("a"), ref("b"), ref("c"), ref("d"), ref("e")))
        // Remove positions 4, 1, 2 (should remove d, b, c regardless of order)
        val removed = repo.removeAtPositions(id, listOf(4, 1, 2))
        assertEquals(3, removed)
        val tracks = repo.getTracks(id)
        assertEquals(2, tracks.size)
        assertEquals(listOf("a", "d"), tracks.map { it.ref.nativeId })
    }
}
