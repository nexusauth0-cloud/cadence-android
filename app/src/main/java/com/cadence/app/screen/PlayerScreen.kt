package com.cadence.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.ProgressControl
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.PlayerViewState
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.status

/**
 * Full player (§8.2 / Phase 5D §5). Built entirely on the presentation contract
 * ([PlaybackViewModel.playerUiState] + [PlaybackViewModel.queue]) — it never
 * touches the media/Media3 layer directly. Renders:
 *  - artwork placeholder / title / artist;
 *  - per-state status line ("Loading…", "Not available", offline badge, etc.);
 *  - playlist/radio context label ("Playing from <playlist>");
 *  - seek (enabled only when `actions.canSeek`) + transport row;
 *  - a queue entry that opens the queue sheet.
 * All primary controls are ≥48dp; secondary ≥40dp; text is sp-based.
 */
@Composable
fun PlayerScreen(
    factory: ViewModelProvider.Factory,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    contextLabel: String?,
    modifier: Modifier = Modifier,
) {
    val playbackViewModel: PlaybackViewModel = viewModel(factory = factory)
    val ui by playbackViewModel.playerUiState.collectAsState()
    val positionMs by playbackViewModel.positionMs.collectAsState()
    val track by playbackViewModel.currentTrack.collectAsState()

    val view = ui.view
    val actions = ui.actions

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = CadenceTokens.Space24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                    )
                }
                Spacer(Modifier.weight(1f))
                if (actions.canOpenQueue) {
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Open queue",
                        )
                    }
                }
            }

            Spacer(Modifier.height(CadenceTokens.Space32))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(CadenceTokens.RadiusLg))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(CadenceTokens.Space32))

            Text(
                text = track?.title ?: "Nothing loaded",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = track?.displayArtist() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )

            contextLabel?.let { label ->
                Spacer(Modifier.height(CadenceTokens.Space8))
                Text(
                    text = "Playing from $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(CadenceTokens.Space16))

            PlayerStatusLine(view)

            Spacer(Modifier.height(CadenceTokens.Space16))

            ProgressControl(
                positionMs = positionMs,
                durationMs = track?.durationMs,
                enabled = actions.canSeek,
                onSeek = playbackViewModel::seekTo,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(CadenceTokens.Space16))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = playbackViewModel::skipToPrevious,
                    enabled = actions.canSkipPrevious,
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(48.dp),
                    )
                }
                PlayPauseControl(
                    canPlay = actions.canPlay,
                    canPause = actions.canPause,
                    onToggle = playbackViewModel::togglePlayPause,
                )
                IconButton(
                    onClick = playbackViewModel::skipToNext,
                    enabled = actions.canSkipNext,
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            if (actions.canRetry) {
                Spacer(Modifier.height(CadenceTokens.Space8))
                IconButton(onClick = playbackViewModel::retry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                }
            }
        }
    }
}

@Composable
private fun PlayPauseControl(canPlay: Boolean, canPause: Boolean, onToggle: () -> Unit) {
    if (!canPlay && !canPause) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(CadenceTokens.RadiusFull))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp,
            )
        }
        return
    }
    val playing = canPause
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(CadenceTokens.RadiusFull))
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun PlayerStatusLine(view: PlayerViewState) {
    val status = view.status()
    if (status.headline.isBlank()) return
    Text(
        text = status.headline,
        style = MaterialTheme.typography.bodyMedium,
        color = when (view) {
            is PlayerViewState.Failed,
            is PlayerViewState.Unavailable,
            -> MaterialTheme.colorScheme.error
            is PlayerViewState.OfflinePlaying,
            is PlayerViewState.OfflinePaused,
            -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
    )
}
