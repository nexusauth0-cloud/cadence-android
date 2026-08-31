package com.cadence.core.domain

import com.cadence.core.database.InMemoryLibraryRepository
import com.cadence.core.database.InMemoryPlaylistRepository
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.database.PlaylistTrack
import com.cadence.core.database.AddedReason
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryUseCasesTest {

    private fun ref(id: String) = TrackRef(ProviderId("demo"), id)
    private fun track(id: String, title: String = "T$id") = Track(
        ref = ref(id),
        title = title,
        artists = listOf(ArtistRef("Artist")),
        durationMs = 1000,
    )

    @Test
    fun saveAndObserveLibrary() = runTest {
        val lib = InMemoryLibraryRepository()
        SaveTrackUseCase(lib)(track("a", "Alpha"))
        SaveTrackUseCase(lib)(track("b", "Beta"))
        val saved = ObserveLibraryUseCase(lib)().first()
        assertEquals(listOf("b", "a"), saved.map { it.ref.nativeId })
        assertEquals("Alpha", saved.last().cachedTitle)
    }

    @Test
    fun unsaveRemoves() = runTest {
        val lib = InMemoryLibraryRepository()
        SaveTrackUseCase(lib)(track("a"))
        assertTrue(UnsaveTrackUseCase(lib)(track("a")))
        assertFalse(UnsaveTrackUseCase(lib)(track("a")))
    }
}

class HistoryUseCasesTest {
    private fun ref(id: String) = TrackRef(ProviderId("demo"), id)
    private fun track(id: String) = Track(ref = ref(id), title = "T$id")

    @Test
    fun recordPlayIsObserved() = runTest {
        val lib = InMemoryLibraryRepository()
        RecordPlayHistoryUseCase(lib)(track("a"))
        RecordPlayHistoryUseCase(lib)(track("b"))
        val history = ObserveHistoryUseCase(lib)().first()
        assertEquals(listOf("b", "a"), history.map { it.ref.nativeId })
    }

    @Test
    fun clearRemovesAll() = runTest {
        val lib = InMemoryLibraryRepository()
        RecordPlayHistoryUseCase(lib)(track("a"))
        assertEquals(1, ClearHistoryUseCase(lib)())
        assertEquals(0, ObserveHistoryUseCase(lib)().first().size)
    }
}

class PlaylistUseCasesTest {
    private fun ref(id: String) = TrackRef(ProviderId("demo"), id)
    private fun track(id: String) = Track(ref = ref(id), title = "T$id")

    @Test
    fun createTitleRenameDeleteLifecycle() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = CreatePlaylistUseCase(repo)("My List")
        assertEquals("My List", GetPlaylistMetadataUseCase(repo)(id)?.title)
        RenamePlaylistUseCase(repo)(id, "Renamed", "desc")
        assertEquals("Renamed", GetPlaylistMetadataUseCase(repo)(id)?.title)
        assertTrue(DeletePlaylistUseCase(repo)(id))
        assertNull(GetPlaylistMetadataUseCase(repo)(id))
    }

    @Test
    fun addToPlaylistDetectsDuplicates() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = CreatePlaylistUseCase(repo)("List")
        val t1 = track("a")
        val t2 = track("b")

        val first = AddToPlaylistUseCase(repo)(id, listOf(t1, t2))
        assertTrue(first.added.containsAll(listOf(ref("a"), ref("b"))))
        assertTrue(first.alreadyPresent.isEmpty())
        assertFalse(first.partial)

        // Re-adding the same track -> already present, not duplicated.
        val second = AddToPlaylistUseCase(repo)(id, listOf(t1))
        assertTrue(second.alreadyPresent.contains(ref("a")))
        assertTrue(second.added.isEmpty())
        assertEquals(2, GetPlaylistTracksUseCase(repo)(id).size)

        // Mixed -> partial result.
        val third = AddToPlaylistUseCase(repo)(id, listOf(t1, track("c")))
        assertTrue(third.partial)
        assertTrue(third.added.contains(ref("c")))
        assertTrue(third.alreadyPresent.contains(ref("a")))
    }

    @Test
    fun addToPlaylistStoresCachedDisplayMetadata() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = CreatePlaylistUseCase(repo)("List")
        val t = Track(ref = ref("x"), title = "Cached Title", artists = listOf(ArtistRef("A")), durationMs = 1234)
        AddToPlaylistUseCase(repo)(id, listOf(t))
        val pt = GetPlaylistTracksUseCase(repo)(id).single()
        assertEquals("Cached Title", pt.title)
        assertEquals("A", pt.artist)
        assertEquals(1234, pt.durationMs)
        // Reconstructed Track is playable without network.
        assertEquals("Cached Title", pt.toTrack()?.title)
    }

    @Test
    fun reorderPersistsMove() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = CreatePlaylistUseCase(repo)("List")
        AddToPlaylistUseCase(repo)(id, listOf(track("a"), track("b"), track("c"), track("d")))
        // Move D (index 3) to position 1 -> A D B C
        assertTrue(ReorderPlaylistUseCase(repo)(id, fromPosition = 3, toPosition = 1))
        val order = GetPlaylistTracksUseCase(repo)(id).map { it.ref.nativeId }
        assertEquals(listOf("a", "d", "b", "c"), order)
    }

    @Test
    fun removeTrackFromPlaylist() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = CreatePlaylistUseCase(repo)("List")
        AddToPlaylistUseCase(repo)(id, listOf(track("a"), track("b")))
        assertEquals(1, RemoveTrackFromPlaylistUseCase(repo)(id, ref("a")))
        assertEquals(listOf("b"), GetPlaylistTracksUseCase(repo)(id).map { it.ref.nativeId })
    }
}

class PlaylistPlaybackUseCaseTest {
    private fun pt(id: String, title: String?) = PlaylistTrack(
        ref = TrackRef(ProviderId("demo"), id),
        position = 0,
        addedReason = AddedReason.USER,
        title = title,
    )

    @Test
    fun emptyPlaylistYieldsNull() {
        assertNull(PlayPlaylistUseCase()(emptyList()))
    }

    @Test
    fun buildsOrderedSessionFromStartIndex() {
        val session = PlayPlaylistUseCase()(
            listOf(pt("a", "A"), pt("b", null), pt("c", "C")), // middle is not playable
            startIndex = 1,
        )!!
        assertEquals(listOf("A", "C"), session.tracks.map { it.title })
        // startIndex clamped into the playable range.
        assertEquals(1, session.startIndex)
    }
}
