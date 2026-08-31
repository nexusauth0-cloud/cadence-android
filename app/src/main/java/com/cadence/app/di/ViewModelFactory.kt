package com.cadence.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.cadence.app.viewmodel.AddToPlaylistViewModel
import com.cadence.app.viewmodel.DownloadsViewModel
import com.cadence.app.viewmodel.LibraryViewModel
import com.cadence.app.viewmodel.PlaylistDetailViewModel
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.SearchViewModel
import com.cadence.core.database.PlaylistId

/**
 * Constructs the UI view models from the [AppGraph]. One shared
 * [PlaybackViewModel] is created lazily; the search/library presenters delegate
 * playback into it, so the UI never touches resolution providers directly.
 */
class AppViewModelFactory(
    private val graph: AppGraph,
) : ViewModelProvider.Factory {

    private val playback: PlaybackViewModel by lazy {
        PlaybackViewModel(
            controller = graph.playerController,
            resolution = graph.resolutionPort,
            queueStore = graph.queueStore,
            downloadRepository = graph.downloadRepository,
            recordHistory = graph.recordHistory,
        )
    }

    /** Keyed factory for one playlist detail screen instance (per playlist id). */
    fun detailFactory(playlistId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistDetailViewModel(
                playlistId = PlaylistId(playlistId),
                getMetadata = graph.getPlaylistMetadata,
                getTracks = graph.getPlaylistTracks,
                removeTrack = graph.removeFromPlaylist,
                reorder = graph.reorderPlaylist,
                rename = graph.renamePlaylist,
                playPlaylist = graph.playPlaylist,
                onPlaySession = { session, label -> playback.playSession(session, label) },
            ) as T
    }

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(PlaybackViewModel::class.java) ->
                playback as T

            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(graph.searchUseCase, onPlayTrack = { playback.loadAndPlay(it) }) as T

            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(
                    observeLibrary = graph.observeLibrary,
                    saveTrack = graph.saveTrack,
                    unsaveTrack = graph.unsaveTrack,
                    observeHistory = graph.observeHistory,
                    clearHistory = graph.clearHistory,
                    listPlaylists = graph.listPlaylists,
                    createPlaylist = graph.createPlaylist,
                    deletePlaylist = graph.deletePlaylist,
                    observeIsSavedFor = graph::observeIsSaved,
                ) as T

            modelClass.isAssignableFrom(AddToPlaylistViewModel::class.java) ->
                AddToPlaylistViewModel(
                    listPlaylists = graph.listPlaylists,
                    addToPlaylist = graph.addToPlaylist,
                    createPlaylist = graph.createPlaylist,
                ) as T

            modelClass.isAssignableFrom(DownloadsViewModel::class.java) ->
                DownloadsViewModel(
                    observeDownloads = graph.observeDownloads,
                    downloadStorage = graph.downloadStorage,
                    cancelDownload = graph.cancelDownload,
                    downloadOperations = graph.downloadOperations,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}