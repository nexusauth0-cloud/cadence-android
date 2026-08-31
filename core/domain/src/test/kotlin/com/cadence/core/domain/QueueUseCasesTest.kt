package com.cadence.core.domain

import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueKind
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueUseCasesTest {

    private val provider = ProviderId("test")
    private fun track(native: String, title: String = native) =
        Track(ref = TrackRef(provider, native), title = title)

    @Test
    fun `add to queue appends and preserves current index`() = runTest {
        val store = InMemoryQueueStore()
        val add = AddToQueueUseCase(store)
        add.invoke(track("A"))
        add.invoke(track("B"))
        val s = store.get()!!
        assertEquals(listOf("A", "B"), s.tracks.map { it.ref.nativeId })
        assertEquals(QueueAddedReason.USER, s.tracks[1].addedReason)
    }

    @Test
    fun `add to queue creates a session from scratch`() = runTest {
        val store = InMemoryQueueStore()
        AddToQueueUseCase(store).invoke(track("A"))
        val s = store.get()!!
        assertEquals(listOf("A"), s.tracks.map { it.ref.nativeId })
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun `add to queue does not duplicate an existing ref`() = runTest {
        val store = InMemoryQueueStore()
        val add = AddToQueueUseCase(store)
        add.invoke(track("A"))
        add.invoke(track("A"))
        assertEquals(1, store.get()!!.tracks.size)
    }

    @Test
    fun `play next inserts after now-playing`() = runTest {
        val store = InMemoryQueueStore()
        AddToQueueUseCase(store).invoke(track("A"))
        // now-playing = A (index 0); add B next
        PlayNextUseCase(store).invoke(track("B"))
        val s = store.get()!!
        assertEquals(listOf("A", "B"), s.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `play next inserts after current even with more items queued`() = runTest {
        val store = InMemoryQueueStore()
        val add = AddToQueueUseCase(store)
        val next = PlayNextUseCase(store)
        add.invoke(track("A"))
        add.invoke(track("Z"))
        // current = A (index 0); play B next -> [A, B, Z]
        next.invoke(track("B"))
        assertEquals(listOf("A", "B", "Z"), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `remove from queue deletes an item`() = runTest {
        val store = InMemoryQueueStore()
        val add = AddToQueueUseCase(store)
        add.invoke(track("A"))
        add.invoke(track("B"))
        assertTrue(RemoveFromQueueUseCase(store).invoke(position = 1))
        assertEquals(listOf("A"), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `reorder moves an item transactionally`() = runTest {
        val store = InMemoryQueueStore()
        val add = AddToQueueUseCase(store)
        add.invoke(track("A")); add.invoke(track("B")); add.invoke(track("C")); add.invoke(track("D"))
        assertTrue(ReorderQueueUseCase(store).invoke(fromPosition = 3, toPosition = 1))
        assertEquals(listOf("A", "D", "B", "C"), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `clear removes the queue`() = runTest {
        val store = InMemoryQueueStore()
        AddToQueueUseCase(store).invoke(track("A"))
        ClearQueueUseCase(store).invoke()
        assertNull(store.get())
    }

    @Test
    fun `build session for playlist persists intent`() = runTest {
        val store = InMemoryQueueStore()
        BuildSessionForPlaylistUseCase(store).invoke(
            tracks = listOf(track("A"), track("B"), track("C")),
            startIndex = 1,
            contextLabel = "My Mix",
        )
        val s = store.get()!!
        assertEquals(QueueKind.PLAYLIST, s.kind)
        assertEquals("My Mix", s.continuationSerialized)
        assertEquals(1, s.currentIndex)
        assertEquals("B", s.tracks[s.currentIndex].ref.nativeId)
        assertEquals(3, s.tracks.size)
    }
}
