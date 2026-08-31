package com.cadence.android.roomdb

import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLibraryRepositoryTest {

    private lateinit var database: CadenceDatabase
    private lateinit var repository: LibraryRepository

    private fun ref(id: String) = TrackRef(ProviderId("demo"), id)
    private fun meta(title: String) = LibraryTrackMeta(title = title, artist = "A", durationMs = 1000)

    @Before
    fun setUp() {
        database = CadenceDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = RoomLibraryRepository.forTesting(
            database = database,
            dispatcher = Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndObserve() = runTest {
        repository.save(ref("a"), meta("A"))
        repository.save(ref("b"), meta("B"))
        val saved = repository.observeSaved().first()
        assertEquals(listOf("b", "a"), saved.map { it.ref.nativeId })
        assertTrue(repository.observeIsSaved(ref("a")).first())
    }

    @Test
    fun duplicateSaveIsNoop() = runTest {
        repository.save(ref("a"), meta("A"))
        repository.save(ref("a"), meta("A again"))
        assertEquals(1, repository.savedCount())
        assertEquals("A", repository.observeSaved().first().single().cachedTitle)
    }

    @Test
    fun unsaveRemoves() = runTest {
        repository.save(ref("a"), meta("A"))
        assertTrue(repository.unsave(ref("a")))
        assertFalse(repository.unsave(ref("a")))
        assertFalse(repository.observeIsSaved(ref("a")).first())
        assertEquals(0, repository.savedCount())
    }

    @Test
    fun historyIsPersistedAndOrdered() = runTest {
        repository.recordPlay(ref("a"), meta("A"))
        repository.recordPlay(ref("b"), meta("B"))
        val history = repository.observeHistory().first()
        assertEquals(listOf("b", "a"), history.map { it.ref.nativeId })
        assertEquals(2, repository.historyCount())
    }

    @Test
    fun clearHistoryRemovesAll() = runTest {
        repository.recordPlay(ref("a"), meta("A"))
        assertEquals(1, repository.clearHistory())
        assertEquals(0, repository.historyCount())
    }
}
