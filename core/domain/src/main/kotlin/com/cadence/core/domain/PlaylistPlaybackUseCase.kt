package com.cadence.core.domain

import com.cadence.core.database.PlaylistTrack
import com.cadence.core.model.Track

/**
 * A resolved, in-order playlist run to play. The playlist is the durable source
 * of truth (ADR-0003): the session is a point-in-time ordered view of its
 * tracks plus the index to begin at; advancing is driven by skip commands and
 * the list is re-read on re-entry, never snapshotted lossily into a new queue
 * table.
 */
data class PlaylistSession(
    val tracks: List<Track>,
    /** Index into [tracks] to start at (0-based). */
    val startIndex: Int,
)

/**
 * Build a [PlaylistSession] from the ordered playlist tracks, beginning at
 * [startIndex]. Tracks reconstruct from cached display metadata (offline, no
 * network re-resolution). Returns null when nothing is playable.
 */
class PlayPlaylistUseCase {
    operator fun invoke(playlistTracks: List<PlaylistTrack>, startIndex: Int = 0): PlaylistSession? {
        val tracks = playlistTracks.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return null
        return PlaylistSession(
            tracks = tracks,
            startIndex = startIndex.coerceIn(0, tracks.lastIndex),
        )
    }
}
