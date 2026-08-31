package com.cadence.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.EmptyState
import com.cadence.app.ui.components.ErrorState
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.DownloadsViewModel
import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadState

/**
 * Downloads (§12): the offline/ownership surface. Renders the REAL download
 * store — queued, downloading, completed, failed — and is honest about
 * availability: a track is labeled available offline only when its file is
 * verified complete, never during queued/downloading/failed.
 */
@Composable
fun DownloadsScreen(
    factory: ViewModelProvider.Factory,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: DownloadsViewModel = viewModel(factory = factory)
    val downloads by vm.downloads.collectAsState()
    val storage by vm.storage.collectAsState()
    val error by vm.error.collectAsState()

    if (error != null) {
        ErrorState(error = error!!, modifier = modifier, onRetry = { vm.errorShown() })
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = CadenceTokens.Space16)) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = CadenceTokens.Space24, bottom = CadenceTokens.Space4),
        )
        Text(
            text = "Everything downloaded plays without a connection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (storage != null) {
            StorageStrip(
                storage = storage!!,
                modifier = Modifier.padding(top = CadenceTokens.Space12),
            )
        }

        Spacer(Modifier.size(CadenceTokens.Space8))

        when {
            downloads.isEmpty() -> EmptyState(
                message = "Nothing downloaded yet. Download tracks to play them offline.",
                actionLabel = "Search",
                onAction = onOpenSearch,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CadenceTokens.Space4),
            ) {
                items(downloads, key = { it.id.value }) { download ->
                    DownloadRow(
                        download = download,
                        onCancel = { vm.cancel(download) },
                        onRemove = { vm.remove(download) },
                        onRetry = { vm.retry(download) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageStrip(
    storage: com.cadence.core.database.DownloadStorageReport,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = "Using ${storage.displaySize}, ${storage.downloadCount} downloads"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${storage.displaySize} of ${storage.downloadCount} " +
                (if (storage.downloadCount == 1) "download" else "downloads"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(CadenceTokens.Space8))
        LinearProgressIndicator(
            progress = { storage.utilizationPercent.toFloat() / 100f },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntry,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CadenceTokens.Space8)
            .semantics {
                contentDescription = "${download.meta.title}, ${download.state.displayLabel}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stateIcon(download)
        Spacer(Modifier.width(CadenceTokens.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.meta.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = stateSubtitle(download),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(CadenceTokens.Space8))

        when (download.state) {
            DownloadState.DOWNLOADING,
            DownloadState.QUEUED -> IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel download")
            }
            DownloadState.FAILED,
            DownloadState.CANCELLED -> IconButton(onClick = onRetry) {
                Icon(Icons.Default.Replay, contentDescription = "Retry download")
            }
            DownloadState.COMPLETED -> IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove download")
            }
        }
    }
}

@Composable
private fun stateIcon(download: DownloadEntry) {
    when (download.state) {
        DownloadState.COMPLETED -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        DownloadState.DOWNLOADING,
        DownloadState.QUEUED -> CircularProgressIndicator(
            modifier = Modifier.size(CadenceTokens.Space24),
            strokeWidth = 2.dp,
            progress = {
                val total = download.totalBytes
                if (total != null && total > 0) {
                    (download.progressBytes.toFloat() / total).coerceIn(0f, 1f)
                } else 0f
            },
        )
        DownloadState.FAILED,
        DownloadState.CANCELLED -> Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun stateSubtitle(download: DownloadEntry): String = when (download.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.DOWNLOADING -> {
        val total = download.totalBytes
        if (total != null && total > 0) {
            val pct = (download.progressBytes * 100 / total).coerceIn(0, 100)
            "Downloading · $pct%"
        } else "Downloading"
    }
    DownloadState.COMPLETED -> "Available offline"
    DownloadState.FAILED -> "Failed"
    DownloadState.CANCELLED -> "Cancelled"
}

private val DownloadState.displayLabel: String
    get() = when (this) {
        DownloadState.QUEUED -> "Queued"
        DownloadState.DOWNLOADING -> "Downloading"
        DownloadState.COMPLETED -> "Available offline"
        DownloadState.FAILED -> "Failed"
        DownloadState.CANCELLED -> "Cancelled"
    }
