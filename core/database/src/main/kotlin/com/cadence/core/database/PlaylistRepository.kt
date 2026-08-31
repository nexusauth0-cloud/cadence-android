package com.cadence.core.database

import com.cadence.core.model.TrackRef

/**
 * Playlist persistence port (ADR-0003).
 *
 * Database-backed with Room (deferred to Android phase). This interface
 * is pure domain — no Room entities, no DAOs, no SQL. Implementations
 * map internally to Room entities within `:core:database`.
 *
 * All ID-list operations MUST go through `inBatches()` (ADR-0005) when
 * the caller cannot guarantee <900 IDs.
 */
interface PlaylistRepository {

    /** Create a new empty playlist. Returns the assigned [PlaylistId]. */
    suspend fun create(title: String, description: String = ""): PlaylistId

    /** Read playlist metadata. Returns null if not found. */
    suspend fun getMetadata(id: PlaylistId): PlaylistMetadata?

    /** Replace playlist metadata (title, description). */
    suspend fun updateMetadata(id: PlaylistId, title: String, description: String = "")

    /** Delete a playlist and all its tracks. */
    suspend fun delete(id: PlaylistId): Boolean

    /** Get all tracks in playlist order. */
    suspend fun getTracks(id: PlaylistId): List<PlaylistTrack>

    /** Add a track at the end of the playlist. */
    suspend fun addTrack(id: PlaylistId, ref: TrackRef, reason: AddedReason = AddedReason.USER)

    /** Add multiple tracks at the end, preserving order. */
    suspend fun addTracks(id: PlaylistId, refs: List<TrackRef>, reason: AddedReason = AddedReason.USER)

    /**
     * Add multiple tracks at the end with cached display metadata.
     * Metadata is stored so playlist detail/playback render offline (ADR-0003
     * CACHE COLUMNS); it is a display cache, never identity.
     */
    suspend fun addTracksDetailed(
        id: PlaylistId,
        refs: List<TrackRef>,
        details: Map<TrackRef, LibraryTrackMeta>,
        reason: AddedReason = AddedReason.USER,
    )

    /** Remove all occurrences of a track from the playlist. */
    suspend fun removeTrack(id: PlaylistId, ref: TrackRef): Int

    /** Remove tracks at specific positions (zero-based). Positions are sorted descending before removal. */
    suspend fun removeAtPositions(id: PlaylistId, positions: List<Int>): Int

    /**
     * Move a track from one position to another.
     * @return true if the move was performed, false if positions are out of range.
     */
    suspend fun reorder(id: PlaylistId, fromPosition: Int, toPosition: Int): Boolean

    /** Replace the entire track list atomically (transactional). */
    suspend fun replaceTracks(id: PlaylistId, tracks: List<PlaylistTrack>)

    /**
     * Check which of the given track refs already exist in the playlist.
     * This operation MUST use batching for large ID sets (ADR-0005).
     */
    suspend fun checkDuplicates(id: PlaylistId, refs: List<TrackRef>): List<DuplicateCheckResult>

    /** Count of tracks in the playlist. */
    suspend fun trackCount(id: PlaylistId): Int

    /** List all playlist IDs (for housekeeping/sync). */
    suspend fun listAll(): List<PlaylistMetadata>
}
