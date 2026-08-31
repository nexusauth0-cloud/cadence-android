package com.cadence.source.ytmusic

import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.Capability
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderAuth
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The real YT Music provider, implemented over the NewPipe Extractor library
 * (GPL-3.0, adapter-isolated here per LICENSE_DECISION.md cond.5).
 *
 * Search and resolution run the extractor's OWN parsing logic; only the remote
 * HTTP hop is behind the [YtMusicTransport] seam so CI stays hermetic:
 * - production uses the OkHttp [YtMusicDownloader];
 * - tests use a fixture-replaying downloader (real extractor code, recorded wire).
 *
 * Anonymous-first (ADR-0002 §6): no authentication is required for this MVP
 * flow, so [authState] stays null and no credentials are touched.
 *
 * Cancellation: all extractor work runs through [runInterruptible] on the IO
 * dispatcher (no blocking on coroutine threads, no main-thread network), and
 * [CancellationException] always propagates untouched.
 */
class YtMusicProvider(
    transport: YtMusicTransport,
    override val id: ProviderId = YtMusicMapper.ID,
) : SourceProvider {

    private val downloader: Downloader = transport.downloader()

    init {
        NewPipe.init(downloader)
    }

    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE)

    private val _authState = MutableStateFlow<ProviderAuth?>(null)
    override val authState: StateFlow<ProviderAuth?> = _authState

    override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> =
        runInterruptible(Dispatchers.IO) {
            try {
                val extractor: SearchExtractor = newSearchExtractor(query)
                val itemsPage = if (cursor == null) {
                    extractor.fetchPage()
                    extractor.getInitialPage()
                } else {
                    extractor.getPage(Page((cursor.token)))
                }
                val tracks: List<Track> = itemsPage.items.mapNotNull(YtMusicMapper::searchItem)
                PagedResult(
                    items = tracks,
                    nextCursor = itemsPage.nextPage?.id
                        ?.takeIf { it.isNotBlank() }
                        ?.let { PageCursor(it) },
                )
            } catch (ce: InterruptedException) {
                throw CancellationException("ytmusic search cancelled")
            } catch (e: Throwable) {
                throw YtMusicErrorMapping.mapSuspending(e, "ytmusic.search")
            }
        }

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource =
        runInterruptible(Dispatchers.IO) {
            try {
                val watchUrl = "https://music.youtube.com/watch?v=${ref.nativeId}"
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
                YtMusicMapper.resolvedSource(
                    ref = ref,
                    title = info.name ?: ref.nativeId,
                    artist = info.uploaderName,
                    audioStreams = info.audioStreams,
                )
            } catch (ce: InterruptedException) {
                throw CancellationException("ytmusic resolve cancelled")
            } catch (e: Throwable) {
                throw YtMusicErrorMapping.mapSuspending(e, "ytmusic.resolve")
            }
        }

    private fun newSearchExtractor(query: SearchQuery): SearchExtractor {
        val handler = ServiceList.YouTube
            .getSearchQHFactory()
            .fromQuery(query.text, listOf(MUSIC_SONGS_FILTER), "")
        return ServiceList.YouTube.getSearchExtractor(handler)
    }

    override fun toString(): String = "YtMusicProvider(id=$id)"

    companion object {
        /** Public stable identity, referenced by composition roots (never UI/domain). */
        val ID: ProviderId = ProviderId("ytmusic")

        private const val MUSIC_SONGS_FILTER = "music_songs"
    }
}
