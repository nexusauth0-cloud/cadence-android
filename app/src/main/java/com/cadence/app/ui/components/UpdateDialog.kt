package com.cadence.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cadence.core.domain.update.UpdateInfo
import com.cadence.core.domain.update.UpdateState

/**
 * App-update modal (Phase 5D update foundation). Renders on the observable
 * [UpdateState]:
 *  - Available: title, version, release notes, "Get the update" (store) and
 *    "Later" (deferred). Critical updates show NO dismiss affordance.
 *  - Checking: a brief progress state.
 *  - Failed: a retry / ignore choice.
 * Opening the store is delegated to [onOpenUpdate]; the URL was already
 * validated (https + allow-listed host) by the state machine — no arbitrary
 * APK URLs reach here.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    state: UpdateState,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUpdate: (UpdateInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UpdateState.Available -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = { if (!info.critical) onDismiss() },
                modifier = modifier,
                title = { Text("Update available") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = "Version ${info.version}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                        )
                        if (info.critical) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "This update is required to keep using Cadence.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onOpenUpdate(info) }) {
                        Text(info.linkLabel)
                    }
                },
                dismissButton = {
                    if (!info.critical) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                },
            )
        }
        is UpdateState.Checking -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Checking for updates…") },
                text = { CircularProgressIndicator(modifier = Modifier.size(32.dp)) },
                confirmButton = {},
            )
        }
        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Couldn't check for updates") },
                text = {
                    Text(
                        text = state.message ?: "Please try again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = { TextButton(onClick = onCheck) { Text("Try again") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
            )
        }
        UpdateState.Hidden,
        UpdateState.NotAvailable,
        -> Unit
    }
}
