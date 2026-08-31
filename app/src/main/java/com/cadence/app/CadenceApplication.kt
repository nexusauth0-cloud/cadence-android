package com.cadence.app

import android.app.Application
import com.cadence.android.player.createMedia3PlayerController
import com.cadence.android.roomdb.RoomDownloadRepository
import com.cadence.android.roomdb.RoomLibraryRepository
import com.cadence.android.roomdb.RoomPlaylistRepository
import com.cadence.android.roomdb.RoomQueueStore
import com.cadence.android.worker.DownloadService
import com.cadence.app.di.AppGraph
import com.cadence.core.media.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process composition root. Builds the single player (ADR-0006 §1), the real
 * Room-backed repositories, and the [AppGraph] that wires every UI dependency.
 */
class CadenceApplication : Application() {

    lateinit var graph: AppGraph
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val controller: PlayerController = createMedia3PlayerController(this, appScope)
        graph = AppGraph(
            playerController = controller,
            libraryRepository = RoomLibraryRepository.open(this),
            playlistRepository = RoomPlaylistRepository.open(this),
            downloadRepository = RoomDownloadRepository.open(this),
            queueStore = RoomQueueStore.open(this),
        )
        graph.bindDownloadOperations(DownloadService(graph.downloadRepository, this))
    }
}
