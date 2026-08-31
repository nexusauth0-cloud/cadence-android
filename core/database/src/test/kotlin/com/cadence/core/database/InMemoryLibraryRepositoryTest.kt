package com.cadence.core.database

import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLibraryRepositoryTest {

    private val lib = InMemoryLibraryRepository()

    private fun ref(id: String = "t") = TrackRef(ProviderId("demo"), id)
    private fun meta(title: String) = LibraryTrackMeta(title = title, artist = "Artist")

    @Test
    fun savePersistsAndDuplicatesAreNoop() = runTest {
        lib.save(ref("a"), meta("A"))
        lib.save(ref("b"), meta("B"))
        lib.save(ref("a"), meta("A again")) // duplicate -> no-op
        assertEquals(2, lib.savedCount())
        assertTrue(lib.observeIsSaved(ref("a")).first())
        assertTrue(lib.observeIsSaved(ref("b")).first())
    }

    @Test
    fun unsaveRemovesAndReports() = runTest {
        lib.save(ref("a"), meta("A"))
        assertTrue(lib.unsave(ref("a")))
        assertFalse(lib.unsave(ref("a")))
        assertFalse(lib.observeIsSaved(ref("a")).first())
        assertEquals(0, lib.savedCount())
    }

    @Test
    fun observeSavedMostRecentFirst() = runTest {
        lib.save(ref("a"), meta("A"))
        lib.save(ref("b"), meta("B"))
        val saved = lib.observeSaved().first()
        assertEquals(listOf("b", "a"), saved.map { it.ref.nativeId })
    }

    @Test
    fun historyRecordsPlaysInOrder() = runTest {
        lib.recordPlay(ref("a"), meta("A"))
        lib.recordPlay(ref("b"), meta("B"))
        val history = lib.observeHistory().first()
        assertEquals(listOf("b", "a"), history.map { it.ref.nativeId })
    }

    @Test
    fun clearHistoryRemovesAll() = runTest {
        lib.recordPlay(ref("a"), meta("A"))
        assertEquals(1, lib.clearHistory())
        assertEquals(0, lib.historyCount())
    }
}
