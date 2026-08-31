package com.cadence.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.core.database.QueueSession
import com.cadence.core.model.TrackRef

/**
 * Queue sheet (ADR-0003 / Phase 5D §6). Renders the durable [QueueSession]:
 * now-playing + up next with the source context ("Playing from <playlist>").
 * Reordering is exposed as explicit move-up/move-down actions with spoken
 * position announcements (deterministic) rather than a fragile drag gesture;
 * each row can play or remove, and the whole queue can be cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    session: QueueSession?,
    nowRef: TrackRef?,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = session?.tracks.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CadenceTokens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(CadenceTokens.Space8))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val context = session?.contextLabel
                if (context != null) {
                    Text(
                        text = "Playing from $context",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            TextButton(onClick = onClear, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Default.ClearAll, contentDescription = null)
                Spacer(Modifier.width(CadenceTokens.Space4))
                Text("Clear")
            }
        }

        Spacer(Modifier.height(CadenceTokens.Space8))

        if (tracks.isEmpty()) {
            Text(
                text = "Your queue is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(CadenceTokens.Space16),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(tracks, key = { _, item -> item.ref.nativeId }) { index, item ->
                    val isNow = item.ref == nowRef
                    val isLast = index == tracks.lastIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                // Spoken position announcement for screen readers.
                                contentDescription =
                                    "${if (isNow) "Now playing, " else ""}${index + 1} of ${tracks.size}, ${item.meta.title ?: "Unknown track"}"
                            }
                            .padding(horizontal = CadenceTokens.Space16, vertical = CadenceTokens.Space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(CadenceTokens.RadiusSm),
                            color = if (isNow) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.padding(CadenceTokens.Space8).size(18.dp),
                                tint = if (isNow) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(CadenceTokens.Space12))
                        Column(modifier = Modifier.weight(1f)) {
                            if (isNow) {
                                Text(
                                    text = "Now playing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = item.meta.title ?: "Unknown track",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            item.meta.artist?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        IconButton(onClick = { onPlay(index) }) {
                            Icon(
                                imageVector = if (isNow) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isNow) "Pause" else "Play ${item.meta.title ?: "track"}",
                            )
                        }
                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = !isLast,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move ${item.meta.title ?: "track"} down",
                            )
                        }
                        IconButton(enabled = index > 0, onClick = { onMoveUp(index) }) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move ${item.meta.title ?: "track"} up",
                            )
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove ${item.meta.title ?: "track"} from queue",
                            )
                        }
                        Spacer(Modifier.width(CadenceTokens.Space4))
                    }
                }
            }
        }
        Spacer(Modifier.height(CadenceTokens.Space16))
    }
}
