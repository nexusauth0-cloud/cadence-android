package com.cadence.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.cadence.app.ui.theme.CadenceTokens

/**
 * Library (§10): the owned local collection — the product's core. The region
 * routes between its own sub-screens (Songs / Playlists / History) and playlist
 * detail; Albums and Artists are shown honestly as "Soon" (this slice does not
 * store album/artist grouping). Only real data is counted — never fake numbers.
 */
@Composable
fun LibraryScreen(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    // "root" | "songs" | "playlists" | "history" | "detail"
    var route by rememberSaveable { mutableStateOf("root") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    when (route) {
        "root" -> LibraryHome(
            factory = factory,
            onSongs = { route = "songs" },
            onPlaylists = { route = "playlists" },
            onHistory = { route = "history" },
            modifier = modifier,
        )
        "songs" -> SongsScreen(factory = factory, onBack = { route = "root" }, modifier = modifier)
        "playlists" -> PlaylistsScreen(
            factory = factory,
            onBack = { route = "root" },
            onOpenPlaylist = { id ->
                detailId = id
                route = "detail"
            },
            modifier = modifier,
        )
        "history" -> HistoryScreen(factory = factory, onBack = { route = "root" }, modifier = modifier)
        "detail" -> {
            val id = detailId
            if (id == null) {
                route = "playlists"
            } else {
                PlaylistDetailScreen(
                    factory = factory,
                    playlistId = id,
                    onBack = {
                        detailId = null
                        route = "playlists"
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun LibraryHome(
    factory: ViewModelProvider.Factory,
    onSongs: () -> Unit,
    onPlaylists: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryViewModel: com.cadence.app.viewmodel.LibraryViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val saved by libraryViewModel.savedTracks.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    val history by libraryViewModel.history.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CadenceTokens.Space16)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = CadenceTokens.Space24, bottom = CadenceTokens.Space8),
        )

        com.cadence.app.ui.components.LibrarySectionRow(
            label = "Songs",
            count = saved.size.toString(),
            onClick = onSongs,
        )
        com.cadence.app.ui.components.LibrarySectionRow(
            label = "Albums",
            onClick = {},
        )
        com.cadence.app.ui.components.LibrarySectionRow(
            label = "Artists",
            onClick = {},
        )
        com.cadence.app.ui.components.LibrarySectionRow(
            label = "Playlists",
            count = playlists.size.toString(),
            onClick = onPlaylists,
        )
        com.cadence.app.ui.components.LibrarySectionRow(
            label = "History",
            count = history.size.toString(),
            onClick = onHistory,
        )

        Spacer(Modifier.height(CadenceTokens.Space24))
        Text(
            text = "Albums and Artists arrive in a later phase. Adding albums and artists is not part of this slice.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CadenceTokens.Space8))
        Text(
            text = "Everything here is stored on this device. This is only a visual guide of where things live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}