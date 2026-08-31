package com.cadence.app

import com.cadence.core.database.InMemoryLibraryRepository
import com.cadence.core.database.InMemoryPlaylistRepository
import com.cadence.core.database.PlaylistId
import com.cadence.core.domain.ClearHistoryUseCase
import com.cadence.core.domain.CreatePlaylistUseCase
import com.cadence.core.domain.DeletePlaylistUseCase
import com.cadence.core.domain.ListPlaylistsUseCase
import com.cadence.core.domain.ObserveHistoryUseCase
import com.cadence.core.domain.ObserveLibraryUseCase
import com.cadence.core.domain.SaveTrackUseCase
import com.cadence.core.domain.UnsaveTrackUseCase
import com.cadence.app.viewmodel.LibraryViewModel
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Library presenters: save/unsave, playlist list mutations, history clearing. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun track(id: String) = Track(ref = TrackRef(ProviderId("demo"), id), title = "T$id")

    private fun vm(
        library: InMemoryLibraryRepository = InMemoryLibraryRepository(),
        playlists: InMemoryPlaylistRepository = InMemoryPlaylistRepository(),
    ) = LibraryViewModel(
        observeLibrary = ObserveLibraryUseCase(library),
        saveTrack = SaveTrackUseCase(library),
        unsaveTrack = UnsaveTrackUseCase(library),
        observeHistory = ObserveHistoryUseCase(library),
        clearHistory = ClearHistoryUseCase(library),
        listPlaylists = ListPlaylistsUseCase(playlists),
        createPlaylist = CreatePlaylistUseCase(playlists),
        deletePlaylist = DeletePlaylistUseCase(playlists),
        observeIsSavedFor = library::observeIsSaved,
    )

    @Test
    fun `saveThenToggleRemoves`() = runTest {
        val library = InMemoryLibraryRepository()
        val viewModel = vm(library)

        viewModel.save(track("a"))
        assertEquals(1, library.savedCount())
        assertTrue(viewModel.observeIsSaved(track("a").ref).first())

        viewModel.toggleSave(track("a"))
        assertEquals(0, library.savedCount())
    }

    @Test
    fun `create and delete playlists refresh the list`() = runTest {
        val playlists = InMemoryPlaylistRepository()
        val viewModel = vm(playlists = playlists)

        viewModel.requestCreatePlaylist("Road Trip")
        assertEquals(1, viewModel.playlists.value.size)

        val id = playlists.create("Manual")
        viewModel.requestDeletePlaylist(PlaylistId(id.value))
        assertEquals(1, viewModel.playlists.value.size)
    }

    @Test
    fun `clearHistory empties and reports`() = runTest {
        val library = InMemoryLibraryRepository()
        library.recordPlay(TrackRef(ProviderId("demo"), "x"), com.cadence.core.database.LibraryTrackMeta("X"))
        val viewModel = vm(library)

        viewModel.requestClearHistory()
        assertEquals(0, viewModel.history.value.size)
    }
}