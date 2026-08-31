package com.cadence.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.EmptyState
import com.cadence.app.ui.components.DownloadToggle
import com.cadence.app.ui.components.SavedHeartButton
import com.cadence.app.ui.components.TrackOverflowMenu
import com.cadence.app.ui.components.TrackRow
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.LibraryViewModel
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.isPlaying
import com.cadence.core.database.PlayHistoryEntry
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.database.SavedTrack
import com.cadence.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/** Screen chrome for Library sub-screens: back arrow + title (+ trailing actions). */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = CadenceTokens.Space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
    Spacer(Modifier.width(CadenceTokens.Space8))
}

/** Drives transient one-shot message flows into a Snackbar. */
@Composable
fun SnackbarObserver(
    message: StateFlow<String?>,
    onShown: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val current by message.collectAsState()
    LaunchedEffect(current) {
        current?.let {
            snackbarHostState.showSnackbar(it)
            onShown()
        }
    }
}

@Composable
fun SongsScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playbackVm: PlaybackViewModel = viewModel(factory = factory)
    val downloadsVm: com.cadence.app.viewmodel.DownloadsViewModel = viewModel(factory = factory)
    val saved by libraryVm.savedTracks.collectAsState()
    val downloads by downloadsVm.downloads.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val currentTrack by playbackVm.currentTrack.collectAsState()
    val playState by playbackVm.playerUiState.collectAsState()
    val preparing by playbackVm.preparing.collectAsState()
    var addToPending by remember { mutableStateOf<List<Track>?>(null) }
    var onlyOffline by rememberSaveable { mutableStateOf(false) }

    // Offline-only filter: show only saved tracks whose download is VERIFIED
    // complete (truthful availability, never during queued/downloading/failed).
    val offlineRefs = remember(downloads) {
        downloads.filter { it.state == com.cadence.core.database.DownloadState.COMPLETED && it.isPlayableOffline }
            .map { "${it.ref.providerId.value}:${it.ref.nativeId}" }
            .toSet()
    }
    val visible = if (onlyOffline) {
        saved.filter { "${it.ref.providerId.value}:${it.ref.nativeId}" in offlineRefs }
    } else saved

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = CadenceTokens.Space16)) {
                ScreenHeader("Songs", onBack)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${offlineRefs.size} available offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = onlyOffline,
                        onClick = { onlyOffline = !onlyOffline },
                        label = { Text("Only offline") },
                    )
                }
                if (visible.isEmpty()) {
                    val msg = if (onlyOffline) {
                        "No songs available offline yet. Download songs to see them here."
                    } else {
                        "No songs saved yet. Find a track in Search and hit the heart."
                    }
                    EmptyState(msg)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { "${it.ref.providerId.value}:${it.ref.nativeId}" }) { savedTrack: SavedTrack ->
                            val track = savedTrack.toTrack()
                            val playing = currentTrack?.ref == track.ref && playState.view.isPlaying
                            val availability = downloadsVm.availabilityForKey(track.ref)
                            TrackRow(
                                track = track,
                                onClick = { playbackVm.loadAndPlay(track) },
                                playing = playing,
                                preparing = preparing && currentTrack?.ref == track.ref,
                                trailing = {
                                    SavedHeartButton(saved = true, onToggle = { libraryVm.unsave(track) })
                                    DownloadToggle(
                                        track = track,
                                        availability = availability,
                                        onDownload = { downloadsVm.download(track) },
                                        onCancel = { downloadsVm.cancelForRef(track.ref) },
                                        onRemove = { downloadsVm.removeByRef(track.ref) },
                                        onRetry = { downloadsVm.download(track) },
                                    )
                                    TrackOverflowMenu(onAddToPlaylist = { addToPending = listOf(track) })
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarObserver(libraryVm.message, libraryVm::messageShown, snackbar)
    }
    addToPending?.let { tracks ->
        AddToPlaylistDialogFor(factory, tracks) { addToPending = null }
    }
}

@Composable
private fun AddToPlaylistDialogFor(
    factory: ViewModelProvider.Factory,
    tracks: List<Track>,
    onDismiss: () -> Unit,
) {
    com.cadence.app.ui.components.AddToPlaylistDialog(factory = factory, tracks = tracks, onDismiss = onDismiss)
}

@Composable
fun HistoryScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playbackVm: PlaybackViewModel = viewModel(factory = factory)
    val history by libraryVm.history.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val currentTrack by playbackVm.currentTrack.collectAsState()
    val playState by playbackVm.playerUiState.collectAsState()
    val preparing by playbackVm.preparing.collectAsState()
    val currentTrackRef = currentTrack?.ref
    var confirmClear by remember { mutableStateOf(false) }
    var addToPending by remember { mutableStateOf<List<Track>?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = CadenceTokens.Space16)) {
                ScreenHeader(
                    title = "History",
                    onBack = onBack,
                    actions = {
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                        }
                    },
                )
                if (history.isEmpty()) {
                    EmptyState("Nothing played yet. Play something from Search and it appears here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(history, key = { "${it.ref.providerId.value}:${it.ref.nativeId}:${it.playedAt}" }) { entry: PlayHistoryEntry ->
                            val track = entry.toTrack()
                            val playing = currentTrackRef == track.ref && playState.view.isPlaying
                            TrackRow(
                                track = track,
                                onClick = { playbackVm.loadAndPlay(track) },
                                playing = playing,
                                preparing = preparing && currentTrackRef == track.ref,
                                trailing = {
                                    TrackOverflowMenu(onAddToPlaylist = { addToPending = listOf(track) })
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarObserver(libraryVm.message, libraryVm::messageShown, snackbar)
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all played tracks from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    libraryVm.requestClearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
    addToPending?.let { tracks -> AddToPlaylistDialogFor(factory, tracks) { addToPending = null } }
}

@Composable
fun PlaylistsScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playlists by libraryVm.playlists.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var deletePending by remember { mutableStateOf<PlaylistMetadata?>(null) }
    var newTitle by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = CadenceTokens.Space16)) {
                ScreenHeader(
                    title = "Playlists",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New playlist")
                        }
                    },
                )
                if (playlists.isEmpty()) {
                    EmptyState("No playlists yet. Create one or add tracks from Search.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(playlists, key = { it.id.value }) { playlist: PlaylistMetadata ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id.value) },
                                onDelete = { deletePending = playlist },
                            )
                        }
                    }
                }
            }
        }
        SnackbarObserver(libraryVm.message, libraryVm::messageShown, snackbar)
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        libraryVm.requestCreatePlaylist(newTitle.trim())
                        newTitle = ""
                        showCreate = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            },
        )
    }

    deletePending?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletePending = null },
            title = { Text("Delete \"${playlist.title}\"?") },
            text = { Text("This removes the playlist and its ordering from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    libraryVm.requestDeletePlaylist(playlist.id)
                    deletePending = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletePending = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CadenceTokens.Space16, horizontal = CadenceTokens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete playlist")
        }
    }
}