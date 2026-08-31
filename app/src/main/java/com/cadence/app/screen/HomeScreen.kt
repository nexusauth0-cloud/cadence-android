package com.cadence.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.app.ui.components.EmptyState
import com.cadence.app.ui.theme.CadenceTokens
import com.cadence.app.viewmodel.PlaybackViewModel

/**
 * Home (§7): a calm, user-structured dashboard. In this vertical slice it
 * surfaces "Continue listening" from the shared player and an honest empty
 * state pointing to Search (no algorithmic rails, no fabricated content).
 */
@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackViewModel: PlaybackViewModel = viewModel(factory = factory)
    val currentTrack by playbackViewModel.currentTrack.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CadenceTokens.Space16)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CadenceTokens.Space12),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = CadenceTokens.Space24, bottom = CadenceTokens.Space8),
        )

        if (currentTrack != null) {
            Text(text = "Continue listening", style = MaterialTheme.typography.titleLarge)
            Card(onClick = { playbackViewModel.setExpanded(true) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(CadenceTokens.Space16)) {
                    Text(
                        text = currentTrack!!.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = currentTrack!!.displayArtist(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        EmptyState(
            message = "Add some music to start building your library.",
            actionLabel = "Search",
            onAction = onOpenSearch,
        )
    }
}
