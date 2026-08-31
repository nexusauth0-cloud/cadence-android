package com.cadence.core.database

import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryQueueStoreTest {

    private val provider = ProviderId("test")
    private fun ref(native: String) = TrackRef(provider, native)
    private fun item(position: Int, native: String, reason: QueueAddedReason = QueueAddedReason.SEED) =
        QueueItem(position = position, ref = ref(native), addedReason = reason)

    private fun session(vararg items: QueueItem, current: Int = -1, kind: QueueKind = QueueKind.MANUAL_LIST) =
        QueueSession(
            kind = kind,
            currentIndex = current,
            tracks = items.map { it.copy(position = it.position) },
        )

    @Test
    fun `save and observe emit the session`() = runTest {
        val store = InMemoryQueueStore()
        val s = session(item(0, "a"), item(1, "b"), current = 0)
        store.save(s)
        assertEquals("a", store.observe().first()?.tracks?.get(0)?.ref?.nativeId)
        assertEquals(s.currentIndex, store.observe().first()?.currentIndex)
    }

    @Test
    fun `get returns the persisted session once`() = runTest {
        val store = InMemoryQueueStore()
        assertNull(store.get())
        val s = session(item(0, "a"), current = 0)
        store.save(s)
        assertEquals(s, store.get())
    }

    @Test
    fun `reorder move D before B yields deterministic ordering`() = runTest {
        val store = InMemoryQueueStore()
        // A B C D ; move D (pos 3) before B (pos 1) -> B stays, D inserted at 1
        store.save(session(item(0, "A"), item(1, "B"), item(2, "C"), item(3, "D"), current = 0))

        assertTrue(store.move(fromPosition = 3, toPosition = 1))

        val after = store.get()!!.tracks.map { it.ref.nativeId }
        assertEquals(listOf("A", "D", "B", "C"), after)
        assertEquals(listOf(0, 1, 2, 3), store.get()!!.tracks.map { it.position })
    }

    @Test
    fun `reorder keeps the current item consistent`() = runTest {
        val store = InMemoryQueueStore()
        // current = B (pos 1); move D (pos 3) before B (pos 1)
        store.save(session(item(0, "A"), item(1, "B"), item(2, "C"), item(3, "D"), current = 1))
        store.move(fromPosition = 3, toPosition = 1)
        // After [A, D, B, C], the current item B is now at index 2; the moved D
        // is now the item B was adjacent to, so current moves with it.
        val after = store.get()!!
        assertEquals("B", after.current?.ref?.nativeId)
    }

    @Test
    fun `reorder validates out-of-range positions`() = runTest {
        val store = InMemoryQueueStore()
        store.save(session(item(0, "A"), item(1, "B")))
        assertFalse(store.move(fromPosition = 5, toPosition = 0))
        assertFalse(store.move(fromPosition = 0, toPosition = 9))
        assertEquals(2, store.get()!!.tracks.size)
    }

    @Test
    fun `removeAt drops an item and renumbers`() = runTest {
        val store = InMemoryQueueStore()
        store.save(session(item(0, "A"), item(1, "B"), item(2, "C"), current = 1))
        assertTrue(store.removeAt(position = 2))
        val after = store.get()!!
        assertEquals(listOf("A", "B"), after.tracks.map { it.ref.nativeId })
        assertEquals(listOf(0, 1), after.tracks.map { it.position })
        // current (B) stays at index 1
        assertEquals(1, after.currentIndex)
    }

    @Test
    fun `removeAt adjusts current index when removing before current`() = runTest {
        val store = InMemoryQueueStore()
        // current = C (index 2); remove A (index 0) -> current becomes index 1
        store.save(session(item(0, "A"), item(1, "B"), item(2, "C"), current = 2))
        store.removeAt(position = 0)
        val after = store.get()!!
        assertEquals("C", after.current?.ref?.nativeId)
        assertEquals(1, after.currentIndex)
    }

    @Test
    fun `removeAt updates current to the next item when removing the current`() = runTest {
        val store = InMemoryQueueStore()
        store.save(session(item(0, "A"), item(1, "B"), item(2, "C"), current = 1))
        store.removeAt(position = 1)
        val after = store.get()!!
        assertEquals("C", after.current?.ref?.nativeId)
        assertEquals(1, after.currentIndex)
    }

    @Test
    fun `clear removes the session`() = runTest {
        val store = InMemoryQueueStore()
        store.save(session(item(0, "A"), current = 0))
        store.clear()
        assertNull(store.get())
    }
}
