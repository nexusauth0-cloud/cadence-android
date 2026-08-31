package com.cadence.app.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.LoadingBlock
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.PlaylistDetailViewModel
import com.cadence.core.database.PlaylistTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Playlist detail (§10): ordered tracks with play, remove, rename, and reorder.
 * Reorder is transactional — every move persists through the repository and the
 * list is re-read (ADR-0003), so the durable order changes, never a UI-only
 * illusion. Drag (long-press handle) and the up/down buttons are equivalent.
 */
@Composable
fun PlaylistDetailScreen(
    factory: ViewModelProvider.Factory,
    playlistId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlaylistDetailViewModel =
        viewModel(
            key = "playlist-$playlistId",
            factory = (factory as? com.cadence.app.di.AppViewModelFactory)?.detailFactory(playlistId) ?: factory,
        )
    val downloadsVm: com.cadence.app.viewmodel.DownloadsViewModel =
        viewModel(factory = factory)

    val tracks by vm.tracks.collectAsState()
    val metadata by vm.metadata.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var renameOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = metadata?.title ?: "Playlist",
                    onBack = onBack,
                    actions = {},
                )
                when {
                    loading -> LoadingBlock()
                    error != null && tracks.isEmpty() -> com.cadence.app.ui.components.ErrorState(
                        error = error!!,
                        onRetry = if (error!!.retryable) ({ vm.load() }) else null,
                    )
                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CadenceTokens.Space16, vertical = CadenceTokens.Space8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                enabled = tracks.isNotEmpty(),
                                onClick = { vm.playAll() },
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(CadenceTokens.Space8))
                                Text("Play all")
                            }
                            TextButton(onClick = {
                                tracks.forEach { pt -> pt.toTrack()?.let { downloadsVm.download(it) } }
                            }) { Text("Download all") }
                            TextButton(onClick = { renameOpen = true }) { Text("Rename") }
                        }
                        if (tracks.isEmpty()) {
                            com.cadence.app.ui.components.EmptyState("This playlist is empty. Add tracks from Search.")
                        } else {
                            PlaylistTrackList(tracks = tracks, vm = vm)
                        }
                        Spacer(Modifier.height(CadenceTokens.Space8))
                        Text(
                            text = "Drag the handle or use the arrows to reorder. Changes save automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = CadenceTokens.Space16, vertical = CadenceTokens.Space8),
                        )
                    }
                }
            }
            SnackbarObserver(vm.message, vm::messageShown, snackbar)
        }
    }

    if (renameOpen) {
        RenameDialog(initial = metadata?.title.orEmpty(), onRenameSaves = { vm.rename(it) }) {
            renameOpen = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackList(
    tracks: List<PlaylistTrack>,
    vm: PlaylistDetailViewModel,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> "${t.ref.providerId.value}:${t.ref.nativeId}${t.position}" }) { index, pt ->
            val track = pt.toTrack()
            var dragAccum by remember { mutableStateOf(0f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(enabled = track != null, onClick = { vm.play(pt) })
                    .padding(vertical = CadenceTokens.Space8, horizontal = CadenceTokens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragAccum = 0f },
                            onDragCancel = { dragAccum = 0f },
                            onDragEnd = { dragAccum = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragAccum += amount.y
                            while (dragAccum >= rowHeightPx && index < tracks.lastIndex) {
                                dragAccum -= rowHeightPx
                                vm.move(index, index + 1)
                            }
                            while (dragAccum <= -rowHeightPx && index > 0) {
                                dragAccum += rowHeightPx
                                vm.move(index, index - 1)
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.Reorder, contentDescription = "Reorder (drag)")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pt.title ?: track?.title ?: "Unavailable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = (pt.artist ?: track?.displayArtist() ?: "").ifEmpty { "Unknown artist" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(
                    enabled = index > 0,
                    onClick = { vm.move(index, index - 1) },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(
                    enabled = index < tracks.lastIndex,
                    onClick = { vm.move(index, index + 1) },
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = { vm.remove(pt) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove from playlist")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onRenameSaves: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onRenameSaves(name.trim())
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}