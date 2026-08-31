package com.cadence.android.roomdb

import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.PlaylistEntity
import com.cadence.android.roomdb.data.PlaylistTrackEntity
import com.cadence.core.common.inBatches
import com.cadence.core.database.AddedReason
import com.cadence.core.database.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies ADR-0005 batching semantics against the REAL Room database:
 * `inBatches` must preserve logical query semantics (0, 1, 500, 900, 999,
 * 1000, 1001, 1200 ids; duplicates; duplicates across batch boundaries) even
 * though SQLite's bound-variable cap is 999. A single unbatched IN would fail;
 * the batching wrapper must not.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistBatchingSemanticsTest {

    private lateinit var database: CadenceDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        database = CadenceDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = RoomPlaylistRepository.forTesting(database = database, dispatcher = Dispatchers.Default)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedPlaylistWith(count: Int): String {
        val dao = database.playlistDao()
        dao.insertPlaylist(PlaylistEntity(id = "p", title = "p", description = "", createdAt = 0, updatedAt = 0))
        val tracks = (0 until count).map { i ->
            PlaylistTrackEntity(
                playlistId = "p",
                position = i,
                providerId = "demo",
                nativeId = "t$i",
                addedReason = AddedReason.USER.name,
            )
        }
        dao.insertTracks(tracks)
        return "p"
    }

    private suspend fun queryAllPresent(playlistId: String, positions: List<Int>): List<Int> =
        inBatches(positions) { batch ->
            database.playlistDao().positionsIn(playlistId, batch).sorted()
        }.sorted()

    @Test
    fun `zero ids returns empty`() = runTest {
        seedPlaylistWith(1)
        assertEquals(emptyList<Int>(), queryAllPresent("p", emptyList()))
    }

    @Test
    fun `one id returns the single present position`() = runTest {
        seedPlaylistWith(1)
        assertEquals(listOf(0), queryAllPresent("p", listOf(0)))
        assertEquals(emptyList<Int>(), queryAllPresent("p", listOf(999)))
    }

    @Test
    fun `500 ids all present`() = runTest {
        seedPlaylistWith(500)
        val all = (0 until 500).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `900 ids all present - single full batch`() = runTest {
        seedPlaylistWith(900)
        val all = (0 until 900).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `999 ids all present`() = runTest {
        seedPlaylistWith(999)
        val all = (0 until 999).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `1000 ids all present - exceeds sqlite cap, batched`() = runTest {
        seedPlaylistWith(1000)
        val all = (0 until 1000).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `1001 ids all present`() = runTest {
        seedPlaylistWith(1001)
        val all = (0 until 1001).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `1200 ids all present - representative large playlist`() = runTest {
        seedPlaylistWith(1200)
        val all = (0 until 1200).toList()
        assertEquals(all, queryAllPresent("p", all))
    }

    @Test
    fun `duplicate ids deduped preserving present set`() = runTest {
        seedPlaylistWith(10)
        val withDuplicates = listOf(0, 0, 0, 1, 1, 5, 9, 9, 99)
        assertEquals(listOf(0, 1, 5, 9), queryAllPresent("p", withDuplicates))
    }

    @Test
    fun `duplicates across batch boundaries do not change result`() = runTest {
        seedPlaylistWith(1200)
        // Positions 899/900 and 999/1000 straddle the 900-item batch boundary.
        val straddlingWithDupes = (898..902).toList() + listOf(899, 900, 999, 1000, 1000, 1001) + (0 until 1200)
        val expected = (0 until 1200).toSet()
        assertEquals(expected, queryAllPresent("p", straddlingWithDupes).toSet())
    }
}
