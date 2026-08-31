package com.cadence.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.AddToPlaylistViewModel
import com.cadence.core.model.Track

/**
 * Add-to-playlist sheet (§9/§10). Honest per-playlist feedback: each row shows
 * "Add", "Added", "Already there", or a partial result — a duplicate is never
 * silently re-inserted. Supports adding the same tracks to multiple playlists,
 * creating a fresh playlist, and cancelling.
 */
@Composable
fun AddToPlaylistDialog(
    factory: ViewModelProvider.Factory,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: AddToPlaylistViewModel = viewModel(factory = factory)
    val playlists by vm.playlists.collectAsState()
    val results by vm.results.collectAsState()
    val loading by vm.loading.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(tracks) {
        if (tracks.isNotEmpty()) vm.open(tracks)
    }

    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            vm.dismiss()
            onDismiss()
        },
        modifier = modifier,
        title = {
            Text(
                text = if (tracks.size == 1) "Add to playlist"
                else "Add ${tracks.size} tracks to playlist",
            )
        },
        text = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New playlist name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(CadenceTokens.Space8))
                    TextButton(
                        enabled = newName.isNotBlank() && !busy,
                        onClick = {
                            vm.createAndAdd(newName.trim())
                            newName = ""
                        },
                    ) {
                        Text("Create")
                    }
                }
                Spacer(Modifier.heightIn(min = CadenceTokens.Space12))
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(CadenceTokens.Space16)) {
                        CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                    }
                    playlists.isEmpty() -> Text(
                        text = "No playlists yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(playlists, key = { it.id.value }) { playlist ->
                            PlaylistPickRow(
                                title = playlist.title,
                                count = playlist.trackCount,
                                result = results[playlist.id.value],
                                busy = busy,
                                onClick = { vm.addTo(playlist.id) },
                            )
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.heightIn(min = CadenceTokens.Space8))
                    Text(
                        text = it.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    vm.dismiss()
                    onDismiss()
                },
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun PlaylistPickRow(
    title: String,
    count: Int,
    result: com.cadence.core.domain.AddToPlaylistResult?,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CadenceTokens.Space12, horizontal = CadenceTokens.Space4),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "$count ${if (count == 1) "track" else "tracks"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.width(20.dp))
            result == null -> TextButton(onClick = onClick, enabled = !busy) { Text("Add") }
            result.added.isEmpty() && result.alreadyPresent.isEmpty() -> TextButton(onClick = {}) { Text("Added") }
            result.added.isEmpty() -> Text(
                text = "Already there",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.alreadyPresent.isEmpty() -> Row {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(CadenceTokens.Space4))
                Text(
                    text = if (result.added.size == 1) "Added" else "Added ${result.added.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> Text(
                text = "Added ${result.added.size} · ${result.alreadyPresent.size} already",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}