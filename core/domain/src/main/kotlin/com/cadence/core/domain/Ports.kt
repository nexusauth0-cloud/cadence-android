package com.cadence.core.domain

import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.PagedResult
import com.cadence.source.api.PageCursor
import com.cadence.source.api.SearchQuery

/** Domain port for search. Implemented over the provider registry in :core:data. */
interface CatalogSearchPort {
    suspend fun search(query: SearchQuery, cursor: PageCursor? = null): PagedResult<Track>
}

/** Domain port for one-shot source resolution. */
interface SourceResolutionPort {
    suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource
}

/**
 * Domain port for download OPERATIONS that have durable side effects
 * (persistence + background execution). The UI depends on this narrow seam —
 * it never touches WorkManager, filesystem paths, or cache tiers.
 *
 * Implemented by the worker module's DownloadService for the production app;
 * tests inject an in-memory fake.
 */
interface DownloadOperationsPort {
    /** Start downloading a track. Idempotent. Returns the downloaded entry id. */
    suspend fun download(track: Track): Long

    /** Cancel an in-progress download. */
    suspend fun cancel(downloadId: Long)

    /** Remove a download entirely (file + record). */
    suspend fun remove(downloadId: Long)

    /** Retry a failed or cancelled download. */
    suspend fun retry(downloadId: Long)

    /** Batch-download all non-completed tracks in a list. Notifies with count on completion. */
    suspend fun downloadAll(tracks: List<Track>): Int
}
