package com.cadence.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.AddToPlaylistDialog
import com.cadence.app.ui.components.EmptyState
import com.cadence.app.ui.components.ErrorState
import com.cadence.app.ui.components.LoadingBlock
import com.cadence.app.ui.components.SavedHeartButton
import com.cadence.app.ui.components.TrackOverflowMenu
import com.cadence.app.ui.components.TrackRow
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.LibraryViewModel
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.SearchUiState
import com.cadence.app.viewmodel.SearchViewModel
import com.cadence.app.viewmodel.isPlaying
import com.cadence.core.model.Track

/**
 * Search front door (§9): debounced query, results as [TrackRow]s, and the
 * tap-to-play slice. Tapping a result starts playback through the shared
 * [PlaybackViewModel] — the UI never sees provider or resolution types.
 */
@Composable
fun SearchScreen(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val searchViewModel: SearchViewModel = viewModel(factory = factory)
    val playbackViewModel: PlaybackViewModel = viewModel(factory = factory)
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)

    val query by searchViewModel.query.collectAsState()
    val uiState by searchViewModel.uiState.collectAsState()
    val playbackState by playbackViewModel.playerUiState.collectAsState()
    val preparing by playbackViewModel.preparing.collectAsState()
    val currentTrack by playbackViewModel.currentTrack.collectAsState()

    var addToPending by remember { mutableStateOf<List<Track>?>(null) }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CadenceTokens.Space16),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = searchViewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search music") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = searchViewModel::clearQuery) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
        }

        when (val state = uiState) {
            SearchUiState.Idle -> EmptyState(
                message = "Search across your music, from any source.",
            )

            SearchUiState.Loading -> LoadingBlock()

            is SearchUiState.Error -> ErrorState(
                error = state.error,
                onRetry = searchViewModel::retry,
            )

            is SearchUiState.Results -> {
                if (state.tracks.isEmpty()) {
                    EmptyState(message = "No results for \"${state.query}\".")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.tracks, key = { "${it.ref.providerId.value}:${it.ref.nativeId}" }) { track: Track ->
                            val playingNow =
                                currentTrack?.ref == track.ref && playbackState.view.isPlaying
                            val saved by libraryViewModel.observeIsSaved(track.ref).collectAsState(initial = false)
                            TrackRow(
                                track = track,
                                onClick = {
                                    focusManager.clearFocus()
                                    searchViewModel.onResultTapped(track)
                                },
                                playing = playingNow,
                                preparing = preparing && currentTrack?.ref == track.ref,
                                trailing = {
                                    SavedHeartButton(
                                        saved = saved,
                                        onToggle = { libraryViewModel.toggleSave(track) },
                                    )
                                    TrackOverflowMenu(onAddToPlaylist = { addToPending = listOf(track) })
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    addToPending?.let { tracks ->
        AddToPlaylistDialog(factory = factory, tracks = tracks, onDismiss = { addToPending = null })
    }
}
