package com.cadence.core.domain

import com.cadence.core.database.PlaylistId
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.database.PlaylistRepository
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef

/**
 * Outcome of an add-to-playlist call, letting the UI honestly distinguish
 * (section 11 "For duplicates"):
 * - all added  -> [added] non-empty, [alreadyPresent] empty (success)
 * - all present -> [added] empty, [alreadyPresent] = requested (already present)
 * - some of each -> partial result
 * Never claims a duplicate was inserted.
 */
data class AddToPlaylistResult(
    val added: List<TrackRef>,
    val alreadyPresent: List<TrackRef>,
) {
    val partial: Boolean get() = added.isNotEmpty() && alreadyPresent.isNotEmpty()
}

/** Create a new empty playlist. Returns the assigned id. */
class CreatePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(title: String, description: String = ""): PlaylistId =
        repository.create(title, description)
}

/** Rename a playlist (wholesale metadata replacement). */
class RenamePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId, title: String, description: String = "") =
        repository.updateMetadata(id, title, description)
}

/** Delete a playlist (and its tracks; the tracks are not removed from Library). */
class DeletePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId): Boolean = repository.delete(id)
}

/** Remove occurrences of a track from a playlist. Returns count removed. */
class RemoveTrackFromPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId, ref: TrackRef): Int = repository.removeTrack(id, ref)
}

/** Reorder a playlist move (transactional, persisted). */
class ReorderPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId, fromPosition: Int, toPosition: Int): Boolean =
        repository.reorder(id, fromPosition, toPosition)
}

/** List all playlists (metadata + counts). */
class ListPlaylistsUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(): List<PlaylistMetadata> = repository.listAll()
}

/** Read a playlist's ordered tracks. */
class GetPlaylistTracksUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId): List<com.cadence.core.database.PlaylistTrack> =
        repository.getTracks(id)
}

/** Read playlist metadata + track count for a detail header. */
class GetPlaylistMetadataUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId): PlaylistMetadata? = repository.getMetadata(id)
}

/**
 * Add [tracks] to a playlist with explicit duplicate handling. Tracks already
 * present are NOT inserted and are reported separately. The repository handles
 * large sets via ADR-0005 batching.
 */
class AddToPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: PlaylistId, tracks: List<Track>): AddToPlaylistResult {
        if (tracks.isEmpty()) return AddToPlaylistResult(emptyList(), emptyList())
        val refs = tracks.map { it.ref }
        val dupes = repository.checkDuplicates(id, refs)
            .filter { it.existingPositions.isNotEmpty() }
            .map { it.ref }
            .toSet()
        val newRefs = refs.filterNot { it in dupes }
        if (newRefs.isNotEmpty()) {
            repository.addTracksDetailed(
                id,
                newRefs,
                tracks.filter { it.ref in newRefs }.associate { it.ref to it.toLibraryMeta() },
            )
        }
        return AddToPlaylistResult(added = newRefs, alreadyPresent = refs.filter { it in dupes })
    }
}
