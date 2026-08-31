package com.cadence.app.di

import com.cadence.core.data.SpiCatalogSearchAdapter
import com.cadence.core.data.SpiResolutionAdapter
import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadRepository
import com.cadence.core.database.InMemoryDownloadRepository
import com.cadence.core.database.InMemoryLibraryRepository
import com.cadence.core.database.InMemoryPlaylistRepository
import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.PlaylistRepository
import com.cadence.core.database.QueueStore
import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.domain.AddToPlaylistUseCase
import com.cadence.core.domain.CancelDownloadUseCase
import com.cadence.core.domain.CatalogSearchPort
import com.cadence.core.domain.ClearHistoryUseCase
import com.cadence.core.domain.CreatePlaylistUseCase
import com.cadence.core.domain.DeletePlaylistUseCase
import com.cadence.core.domain.DownloadOperationsPort
import com.cadence.core.domain.DownloadStorageUseCase
import com.cadence.core.domain.DownloadStatusUseCase
import com.cadence.core.domain.GetPlaylistMetadataUseCase
import com.cadence.core.domain.GetPlaylistTracksUseCase
import com.cadence.core.domain.ListPlaylistsUseCase
import com.cadence.core.domain.ObserveCompletedDownloadsUseCase
import com.cadence.core.domain.ObserveDownloadsUseCase
import com.cadence.core.domain.ObserveHistoryUseCase
import com.cadence.core.domain.ObserveLibraryUseCase
import com.cadence.core.domain.OfflineFirstResolutionPort
import com.cadence.core.domain.PlayPlaylistUseCase
import com.cadence.core.domain.RecordPlayHistoryUseCase
import com.cadence.core.domain.RemoveTrackFromPlaylistUseCase
import com.cadence.core.domain.RenamePlaylistUseCase
import com.cadence.core.domain.ReorderPlaylistUseCase
import com.cadence.core.domain.SaveTrackUseCase
import com.cadence.core.domain.SearchUseCase
import com.cadence.core.domain.SourceResolutionPort
import com.cadence.core.domain.UnsaveTrackUseCase
import com.cadence.core.domain.update.NoopUpdateSource
import com.cadence.core.domain.update.SemanticVersion
import com.cadence.core.domain.update.UpdateManager
import com.cadence.core.domain.update.UpdateSource
import com.cadence.core.media.PlayerController
import com.cadence.source.api.ProviderRegistry
import com.cadence.source.api.DefaultProviderRegistry
import com.cadence.source.api.ProviderScope
import com.cadence.source.api.SourceProvider
import com.cadence.source.ytmusic.YtMusicProvider
import com.cadence.source.ytmusic.YtMusicTransport

/**
 * Application composition root. Owns every cross-cutting dependency the UI
 * layer consumes. This is the ONLY place provider implementations and SPI
 * adapters are assembled — view models and screens depend only on the domain
 * ports and use cases they receive.
 *
 * Provider selection is injectable (constants 1–6, 8):
 * - [providers] is the set of registered implementations;
 * - [defaultScope] pins the single-source default the [SearchUseCase] falls
 *   back to when a caller does not name a scope.
 *
 * The PRODUCTION configuration (the no-arg defaults) registers the real
 * [YtMusicProvider] over live network plus the hermetic demo provider, and pins
 * `ProviderScope.Single(YtMusicProvider.ID)` — so production search targets the
 * real YT Music source and never fans out cross-source. There is NO
 * environment detection or conditional swap in this class: tests simply
 * construct a DIFFERENT [AppGraph] with their own providers/scope.
 *
 * [playerController] is injected so tests can substitute a fake; production
 * wires the real media3-backed controller in [com.cadence.app.CadenceApplication].
 */
class AppGraph(
    val playerController: PlayerController,
    private val providers: List<SourceProvider> = defaultProviders(),
    private val defaultScope: ProviderScope = ProviderScope.Single(YtMusicProvider.ID),
    val libraryRepository: LibraryRepository = InMemoryLibraryRepository(),
    val playlistRepository: PlaylistRepository = InMemoryPlaylistRepository(),
    val downloadRepository: DownloadRepository = InMemoryDownloadRepository(),
    val queueStore: QueueStore = InMemoryQueueStore(),
    val updateSource: UpdateSource = NoopUpdateSource,
) {

    private val registry: ProviderRegistry = DefaultProviderRegistry(providers)

    private val catalog: CatalogSearchPort = SpiCatalogSearchAdapter(registry)
    private val resolution: SourceResolutionPort = SpiResolutionAdapter(registry)

    /**
     * Download OPERATIONS (background execution) — bound by the composition
     * root's production wiring (DownloadService). Tests that don't need real
     * file execution leave this null; the UI reads + cancels still work via
     * the repository-backed use cases.
     */
    @Volatile
    var downloadOperations: DownloadOperationsPort? = null
        private set

    fun bindDownloadOperations(operations: DownloadOperationsPort) {
        this.downloadOperations = operations
    }

    /** App-update state machine (Phase 5D update foundation), source injected. */
    val updateManager: UpdateManager = UpdateManager(
        source = updateSource,
        currentVersion = SemanticVersion.parse(com.cadence.core.domain.update.CURRENT_APP_VERSION)
            ?: SemanticVersion(1, 0, 0),
    )

    val searchUseCase: SearchUseCase = SearchUseCase(catalog, defaultScope)

    // Library (saved tracks).
    val observeLibrary = ObserveLibraryUseCase(libraryRepository)
    val saveTrack = SaveTrackUseCase(libraryRepository)
    val unsaveTrack = UnsaveTrackUseCase(libraryRepository)

    // History.
    val observeHistory = ObserveHistoryUseCase(libraryRepository)
    val recordHistory = RecordPlayHistoryUseCase(libraryRepository)
    val clearHistory = ClearHistoryUseCase(libraryRepository)

    // Playlists.
    val createPlaylist = CreatePlaylistUseCase(playlistRepository)
    val renamePlaylist = RenamePlaylistUseCase(playlistRepository)
    val deletePlaylist = DeletePlaylistUseCase(playlistRepository)
    val listPlaylists = ListPlaylistsUseCase(playlistRepository)
    val getPlaylistTracks = GetPlaylistTracksUseCase(playlistRepository)
    val getPlaylistMetadata = GetPlaylistMetadataUseCase(playlistRepository)
    val addToPlaylist = AddToPlaylistUseCase(playlistRepository)
    val removeFromPlaylist = RemoveTrackFromPlaylistUseCase(playlistRepository)
    val reorderPlaylist = ReorderPlaylistUseCase(playlistRepository)
    val playPlaylist = PlayPlaylistUseCase()

    // Downloads & offline (section 8, 9).
    // Offline-first resolution: prefers a verified local download, otherwise
    // falls back to remote resolve; respects hints.offlineOnly honesty.
    private val offlineFirstResolution: SourceResolutionPort =
        OfflineFirstResolutionPort(downloadRepository, resolution)

    val observeDownloads = ObserveDownloadsUseCase(downloadRepository)
    val observeCompletedDownloads = ObserveCompletedDownloadsUseCase(downloadRepository)
    val downloadStatus = DownloadStatusUseCase(downloadRepository)
    val downloadStorage = DownloadStorageUseCase(downloadRepository)
    val cancelDownload = CancelDownloadUseCase(downloadRepository)

    /** Observe saved-state for a single track, for heart/toggle UI. */
    fun observeIsSaved(ref: com.cadence.core.model.TrackRef) = libraryRepository.observeIsSaved(ref)

    /** The pinned default scope; observable for tests/diagnostics. */
    val searchDefaultScope: ProviderScope = defaultScope

    /** Diagram-B play path: resolve(ref, hints) -> PlayerController.load.
     *  Offline-first: prefers verified local downloads, else remote resolve. */
    val resolutionPort: SourceResolutionPort = offlineFirstResolution

    /** Registered provider ids, for diagnostics/tests. */
    val registeredProviders: Set<com.cadence.core.model.ProviderId> =
        registry.all().map { it.id }.toSet()

    private companion object {
        fun defaultProviders(): List<SourceProvider> =
            AppDemoConfig.providers() + YtMusicProvider(transport = YtMusicTransport.okHttp())
    }
}
