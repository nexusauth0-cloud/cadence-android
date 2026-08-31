package com.cadence.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.cadence.app.di.AppGraph
import com.cadence.app.di.AppViewModelFactory
import com.cadence.app.navigation.AppDestination
import com.cadence.app.screen.DownloadsScreen
import com.cadence.app.screen.HomeScreen
import com.cadence.app.screen.LibraryScreen
import com.cadence.app.screen.PlayerScreen
import com.cadence.app.screen.SearchScreen
import com.cadence.app.ui.components.MiniPlayer
import com.cadence.app.ui.components.QueueSheet
import com.cadence.app.ui.components.UpdateDialog
import com.cadence.app.ui.theme.CadenceTheme
import com.cadence.app.viewmodel.PlayerViewState
import com.cadence.app.viewmodel.PlaybackViewModel
import com.cadence.app.viewmodel.UpdateViewModel
import com.cadence.core.domain.update.UpdateInfo

/**
 * Root adaptive shell (§6): 4-destination navigation that collapses to a rail on
 * large screens, with the mini-player always visible while a track is loaded
 * and a full-player overlay on expand. The full player hosts the queue sheet,
 * which drives the durable queue (ADR-0003).
 */
@Composable
fun CadenceApp(graph: AppGraph, modifier: Modifier = Modifier) {
    CadenceTheme {
        val factory = remember { AppViewModelFactory(graph) }
        val playbackViewModel: PlaybackViewModel = viewModel(factory = factory)
        val expanded by playbackViewModel.expanded.collectAsState()
        var showQueue by rememberSaveable { mutableStateOf(false) }

        val updateViewModel: UpdateViewModel =
            viewModel(factory = UpdateViewModel.factory(graph.updateManager))
        val updateState by updateViewModel.state.collectAsState()
        LaunchedEffect(Unit) { updateViewModel.check() }
        val context = LocalContext.current

        if (expanded) {
            val queue by playbackViewModel.queue.collectAsState()
            PlayerScreen(
                factory = factory,
                onCollapse = { playbackViewModel.setExpanded(false) },
                onOpenQueue = { showQueue = true },
                contextLabel = queue?.contextLabel,
            )
            if (showQueue) {
                QueueSheet(
                    session = queue,
                    nowRef = playbackViewModel.currentTrack.collectAsState().value?.ref,
                    onPlay = playbackViewModel::playQueueItem,
                    onRemove = playbackViewModel::removeAt,
                    onMoveDown = { index -> playbackViewModel.reorder(index, index + 1) },
                    onMoveUp = { index -> playbackViewModel.reorder(index, index - 1) },
                    onClear = playbackViewModel::clearQueue,
                    onDismiss = { showQueue = false },
                )
            }
        } else {
            AppShell(factory, playbackViewModel, modifier)
        }

        UpdateDialog(
            state = updateState,
            onCheck = { updateViewModel.check() },
            onDismiss = { updateViewModel.dismiss() },
            onOpenUpdate = { info -> CadenceAppExtensions.openStoreUrl(context, info) },
        )
    }
}

private object CadenceAppExtensions {
    fun openStoreUrl(context: android.content.Context, info: UpdateInfo) {
        val uri = Uri.parse(info.storeUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }
}

@Composable
private fun AppShell(
    factory: ViewModelProvider.Factory,
    playbackViewModel: PlaybackViewModel,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(AppDestination.Home) }
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    val windowSize: WindowSizeClass = calculateWindowSizeClass(activity = requireNotNull(LocalActivity.current))
    val useRail = windowSize.widthSizeClass == WindowWidthSizeClass.Expanded
    val onOpenSearch = { selected = AppDestination.Search }

    val track by playbackViewModel.currentTrack.collectAsState()
    val ui by playbackViewModel.playerUiState.collectAsState()

    val playing = ui.view is PlayerViewState.Playing || ui.view is PlayerViewState.OfflinePlaying
    val offline = ui.view is PlayerViewState.OfflinePlaying || ui.view is PlayerViewState.OfflinePaused
    val visible = ui.view != PlayerViewState.Nothing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!useRail) {
                BottomNav(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail) {
                RailNav(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selected) {
                        AppDestination.Home -> HomeScreen(factory, onOpenSearch)
                        AppDestination.Search -> SearchScreen(factory)
                        AppDestination.Library -> LibraryScreen(factory)
                        AppDestination.Downloads -> DownloadsScreen(factory, onOpenSearch)
                    }
                }
                MiniPlayer(
                    track = track,
                    visible = visible,
                    playing = playing,
                    offlineAvailable = offline,
                    onTogglePlayPause = playbackViewModel::togglePlayPause,
                    onOpen = { playbackViewModel.setExpanded(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BottomNav(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun RailNav(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationRail {
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
