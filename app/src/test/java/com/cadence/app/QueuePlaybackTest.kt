package com.cadence.app

import com.cadence.app.di.AppDemoConfig
import com.cadence.app.di.AppGraph
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.core.database.InMemoryLibraryRepository
import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.database.QueueKind
import com.cadence.core.model.ArtistRef
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.core.testing.PlayerControllerFake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Phase 5D queue integration on the ViewModel: the durable queue (ADR-0003)
 * is seeded by load/play, mutated by add/play-next/remove/reorder, persisted
 * as a session, and restored on process death. The queue is the source of
 * truth for now-playing/upcoming — not a raw player snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePlaybackTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun ref(id: String) = TrackRef(com.cadence.core.model.ProviderId("demo"), id)
    private fun track(id: String) = Track(ref = ref(id), title = "T$id", artists = listOf(ArtistRef("A")))

    private val A = "alpha-track"
    private val B = "bravo-track"
    private val C = "charlie-track"

    private fun vm(
        controller: PlayerControllerFake,
        queueStore: InMemoryQueueStore,
    ): PlaybackViewModel {
        val graph = AppGraph(
            playerController = controller,
            providers = AppDemoConfig.providers(),
            defaultScope = AppDemoConfig.scope(),
            libraryRepository = InMemoryLibraryRepository(),
        )
        return PlaybackViewModel(
            controller = controller,
            resolution = graph.resolutionPort,
            queueStore = queueStore,
        )
    }

    @Test
    fun `loadAndPlay seeds the durable queue`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))

        val session = store.get()!!
        assertEquals(listOf(A), session.tracks.map { it.ref.nativeId })
        assertEquals(0, session.currentIndex)
    }

    @Test
    fun `addToQueue appends after the seed`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))
        v.addToQueue(track(B))
        v.addToQueue(track(C))

        assertEquals(listOf(A, B, C), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `playNext inserts immediately after now playing`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))
        v.addToQueue(track(C))
        v.playNext(track(B))

        assertEquals(listOf(A, B, C), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `reorder moves an item to another position`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))
        v.addToQueue(track(B))
        v.addToQueue(track(C))

        v.reorder(2, 0) // C to front
        assertEquals(listOf(C, A, B), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `removeAt drops the item`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))
        v.addToQueue(track(B))
        v.removeAt(1)

        assertEquals(listOf(A), store.get()!!.tracks.map { it.ref.nativeId })
    }

    @Test
    fun `playSession persists a PLAYLIST session with context label`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.playSession(
            com.cadence.core.domain.PlaylistSession(tracks = listOf(track(A), track(B)), startIndex = 0),
            contextLabel = "My Mix",
        )

        val session = store.get()!!
        assertEquals(QueueKind.PLAYLIST, session.kind)
        assertEquals("My Mix", session.continuationSerialized)
        assertEquals("My Mix", session.contextLabel)
    }

    @Test
    fun `queue session is restored on ViewModel init`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val pre = com.cadence.core.database.QueueSession(
            kind = QueueKind.PLAYLIST,
            continuationSerialized = "Roadtrip",
            currentIndex = 1,
            tracks = listOf(
                com.cadence.core.database.QueueItem(0, ref(A), com.cadence.core.database.QueueItemMeta("T$A")),
                com.cadence.core.database.QueueItem(1, ref(B), com.cadence.core.database.QueueItemMeta("T$B")),
            ),
        )
        store.save(pre)

        val v = vm(controller, store)

        assertEquals(QueueKind.PLAYLIST, v.queue.first()!!.kind)
        assertEquals("Roadtrip", v.queue.first()!!.contextLabel)
        assertEquals(1, v.sessionIndex.value)
    }

    @Test
    fun `clearQueue empties the queue`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.loadAndPlay(track(A))
        v.addToQueue(track(B))
        v.clearQueue()

        assertNull(store.get())
    }

    @Test
    fun `setNormalization routes through controller and updates state`() = runTest {
        val controller = PlayerControllerFake()
        val store = InMemoryQueueStore()
        val v = vm(controller, store)

        v.setNormalization(true)
        assertEquals(true, controller.lastNormalizationCommand)
        assertEquals(true, v.normalizeAudio.value)

        v.setNormalization(false)
        assertEquals(false, controller.lastNormalizationCommand)
        assertEquals(false, v.normalizeAudio.value)
    }
}
