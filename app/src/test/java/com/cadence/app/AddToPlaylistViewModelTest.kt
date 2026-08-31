package com.cadence.app

import com.cadence.app.viewmodel.AddToPlaylistViewModel
import com.cadence.core.database.InMemoryPlaylistRepository
import com.cadence.core.database.PlaylistId
import com.cadence.core.domain.AddToPlaylistUseCase
import com.cadence.core.domain.CreatePlaylistUseCase
import com.cadence.core.domain.ListPlaylistsUseCase
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Add-to-playlist sheet: honest multi-add + duplicate feedback + create-and-add. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun track(id: String) = Track(ref = TrackRef(ProviderId("demo"), id), title = "T$id")

    @Test
    fun addToReportsAdded() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = repo.create("List")
        val vm = AddToPlaylistViewModel(
            listPlaylists = ListPlaylistsUseCase(repo),
            addToPlaylist = AddToPlaylistUseCase(repo),
            createPlaylist = CreatePlaylistUseCase(repo),
        )
        vm.open(listOf(track("a")))
        vm.addTo(id)

        val result = vm.results.value[id.value]
        assertEquals(1, result?.added?.size)
        assertTrue(result?.alreadyPresent.isNullOrEmpty())
        assertEquals(1, repo.getTracks(id).size)
    }

    @Test
    fun reAddReportsAlreadyThereWithoutDuplicate() = runTest {
        val repo = InMemoryPlaylistRepository()
        val id = repo.create("List")
        val vm = AddToPlaylistViewModel(
            listPlaylists = ListPlaylistsUseCase(repo),
            addToPlaylist = AddToPlaylistUseCase(repo),
            createPlaylist = CreatePlaylistUseCase(repo),
        )
        vm.open(listOf(track("a")))
        vm.addTo(id)
        vm.open(listOf(track("a")))
        vm.addTo(id)

        val result = vm.results.value[id.value]
        assertTrue(result?.alreadyPresent?.isNotEmpty() == true)
        assertTrue(result?.added.isNullOrEmpty())
        // Duplicate never re-inserted.
        assertEquals(1, repo.getTracks(id).size)
    }

    @Test
    fun createAndAddCreatesBlankPlaylistAndAdds() = runTest {
        val repo = InMemoryPlaylistRepository()
        val vm = AddToPlaylistViewModel(
            listPlaylists = ListPlaylistsUseCase(repo),
            addToPlaylist = AddToPlaylistUseCase(repo),
            createPlaylist = CreatePlaylistUseCase(repo),
        )
        vm.open(listOf(track("a"), track("b")))
        vm.createAndAdd("Fresh")

        assertEquals(1, vm.playlists.value.size)
        assertEquals("Fresh", vm.playlists.value.first().title)
        assertEquals(2, repo.listAll().first().trackCount)
    }
}