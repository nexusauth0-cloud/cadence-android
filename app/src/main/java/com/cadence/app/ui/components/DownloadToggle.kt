package com.cadence.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-track download toggle. Surfaces the REAL offline availability from the
 * download store — never a fabricated "available offline":
 *  - verified complete → check (remove on tap)
 *  - downloading/queued  → progress (cancel on tap)
 *  - failed/cancelled     → retry (download on tap)
 *  - not downloaded       → download (download on tap)
 */
@Composable
fun DownloadToggle(
    track: Track,
    availability: StateFlow<OfflineAvailability>,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by availability.collectAsState()

    when (current) {
        is OfflineAvailability.Available -> IconButton(onClick = onRemove, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "${track.title} is downloaded. Remove?",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        is OfflineAvailability.Downloading,
        is OfflineAvailability.Cancelled -> IconButton(onClick = onCancel, modifier = modifier) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
        is OfflineAvailability.Failed,
        OfflineAvailability.Unavailable -> IconButton(onClick = onRetry, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Retry download for ${track.title}",
            )
        }
        OfflineAvailability.NotDownloaded -> IconButton(onClick = onDownload, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download ${track.title}",
            )
        }
    }
}
