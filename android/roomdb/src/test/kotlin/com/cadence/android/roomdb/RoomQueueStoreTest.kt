package com.cadence.android.roomdb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the durable queue contract (ADR-0003): the live in-memory mirror is
 * authoritative for zero-latency ops, and Room is authoritative for restore —
 * a fresh store instance hydrates the exact session (kind, continuation intent,
 * current index, ordered items) that was last saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomQueueStoreTest {

    private val provider = ProviderId("demo")
    private fun ref(id: String) = TrackRef(provider, id)

    private fun buildStore(): Pair<RoomQueueStore, CadenceDatabase> {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CadenceDatabase::class.java,
        ).build()
        return RoomQueueStore.forTesting(db) to db
    }

    private fun session(
        kind: QueueKind = QueueKind.PLAYLIST,
        continuation: String? = "My Mix",
        index: Int = 0,
    ) = QueueSession(
        kind = kind,
        continuationSerialized = continuation,
        currentIndex = index,
        tracks = listOf(
            QueueItem(0, ref("a"), QueueItemMeta("A"), QueueAddedReason.SEED),
            QueueItem(1, ref("b"), QueueItemMeta("B"), QueueAddedReason.USER),
            QueueItem(2, ref("c"), QueueItemMeta("C"), QueueAddedReason.USER),
        ),
    )

    @Test
    fun `save then get round-trips session intent and ordered items`() = runTest {
        val (store, db) = buildStore()
        store.save(session())
        val restored = store.get()!!
        assertEquals(QueueKind.PLAYLIST, restored.kind)
        assertEquals("My Mix", restored.continuationSerialized)
        assertEquals(0, restored.currentIndex)
        assertEquals(listOf("a", "b", "c"), restored.tracks.map { it.ref.nativeId })
        assertEquals(QueueAddedReason.USER, restored.tracks[2].addedReason)
        db.close()
    }

    @Test
    fun `fresh store hydrates exact session from room`() = runTest {
        val (store, db) = buildStore()
        store.save(session(index = 1))
        // Simulate process death: another store instance on the same DB.
        val second = RoomQueueStore.forTesting(db)
        val restored = second.get()!!
        assertEquals(QueueKind.PLAYLIST, restored.kind)
        assertEquals("My Mix", restored.continuationSerialized)
        assertEquals(1, restored.currentIndex)
        assertEquals(listOf("a", "b", "c"), restored.tracks.map { it.ref.nativeId })
        db.close()
    }

    @Test
    fun `reorder persists to room`() = runTest {
        val (store, db) = buildStore()
        store.save(session())
        store.move(2, 0) // C before A
        val second = RoomQueueStore.forTesting(db)
        assertEquals(listOf("c", "a", "b"), second.get()!!.tracks.map { it.ref.nativeId })
        db.close()
    }

    @Test
    fun `removeAt persists to room`() = runTest {
        val (store, db) = buildStore()
        store.save(session())
        store.removeAt(1)
        val second = RoomQueueStore.forTesting(db)
        assertEquals(listOf("a", "c"), second.get()!!.tracks.map { it.ref.nativeId })
        db.close()
    }

    @Test
    fun `clear removes session and items`() = runTest {
        val (store, db) = buildStore()
        store.save(session())
        store.clear()
        assertNull(store.get())
        val second = RoomQueueStore.forTesting(db)
        assertNull(second.get())
        db.close()
    }

    @Test
    fun `observe mirrors saved session`() = runTest {
        val (store, db) = buildStore()
        store.save(session())
        assertEquals(QueueKind.PLAYLIST, store.observe().first()!!.kind)
        db.close()
    }
}
