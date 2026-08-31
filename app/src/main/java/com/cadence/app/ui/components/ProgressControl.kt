package com.cadence.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cadence.app.ui.theme.CadenceTokens
import java.util.Locale

/**
 * Seekable progress control (ADR-0006: position flows, no polling). Duration
 * [durationMs] may be null/zero (indeterminate); [onSeek] fires on user commit.
 */
@Composable
fun ProgressControl(
    positionMs: Long,
    durationMs: Long?,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = (durationMs ?: 0L).coerceAtLeast(0L)
    val fraction = if (total > 0) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    var dragValue by remember { mutableFloatStateOf(fraction) }

    Column(modifier = modifier.semantics { contentDescription = "Seek" }) {
        Slider(
            value = fraction,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                if (total > 0) onSeek((dragValue * total).toLong())
            },
            enabled = enabled && total > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = CadenceTokens.Space8)) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = " / ${formatTime(total)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
