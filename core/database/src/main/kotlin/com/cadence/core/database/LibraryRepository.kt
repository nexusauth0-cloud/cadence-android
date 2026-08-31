package com.cadence.core.database

import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow

/**
 * Library persistence port (favorites + history).
 *
 * Room-backed in production ([SavedTrack]/[PlayHistoryEntry] map to `:android:roomdb`
 * entities); the JVM [InMemoryLibraryRepository] doubles as a spec reference.
 * Like [PlaylistRepository], this interface is pure domain — no Room entities,
 * no DAOs, no SQL leak out of `:core:database`.
 *
 * Collection-parameter operations MUST go through `inBatches()` (ADR-0005) when
 * the caller cannot guarantee <900 IDs.
 *
 * Offline honesty (ADR-0007): saving a track stores metadata ONLY — it does NOT
 * make the track available offline. No method here ever implies downloadable/
 * offline availability.
 */
interface LibraryRepository {

    /** Observe the full set of saved tracks (most-recently-saved first). */
    fun observeSaved(): Flow<List<SavedTrack>>

    /** Observe saved-state for a single track (true when present). */
    fun observeIsSaved(ref: TrackRef): Flow<Boolean>

    /** Insert a saved track. Idempotent: re-saving an existing track is a no-op. */
    suspend fun save(ref: TrackRef, meta: LibraryTrackMeta)

    /** Remove a saved track. Returns true if it was present. */
    suspend fun unsave(ref: TrackRef): Boolean

    /** Number of saved tracks. */
    suspend fun savedCount(): Int

    /** Observe history (most-recent-first). */
    fun observeHistory(): Flow<List<PlayHistoryEntry>>

    /** Record a genuine successful playback start. */
    suspend fun recordPlay(ref: TrackRef, meta: LibraryTrackMeta)

    /** Clear all history (destructive; caller confirms). */
    suspend fun clearHistory(): Int

    /** History size. */
    suspend fun historyCount(): Int
}

/** Display-metadata cache captured at save/record time (offline Library rendering). */
data class LibraryTrackMeta(
    val title: String,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
)
