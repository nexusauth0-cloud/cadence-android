package com.cadence.core.domain

import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.database.SavedTrack
import com.cadence.core.model.Track
import kotlinx.coroutines.flow.Flow

/** Minimal conversion of a [Track] into the display-metadata cache for a save/playlist. */
internal fun Track.toLibraryMeta(): LibraryTrackMeta = LibraryTrackMeta(
    title = title,
    artist = artists.firstOrNull()?.name,
    durationMs = durationMs,
    artworkUrl = artwork?.variants?.firstOrNull()?.url,
)

/** Persist a track into the local library (favorite). Idempotent. */
class SaveTrackUseCase(private val library: LibraryRepository) {
    suspend operator fun invoke(track: Track) = library.save(track.ref, track.toLibraryMeta())
}

/** Remove a track from the local library. Returns true if it was present. */
class UnsaveTrackUseCase(private val library: LibraryRepository) {
    suspend operator fun invoke(track: Track): Boolean = library.unsave(track.ref)
}

/** Observe the full saved-track library (most-recently-saved first). */
class ObserveLibraryUseCase(private val library: LibraryRepository) {
    operator fun invoke(): Flow<List<SavedTrack>> = library.observeSaved()
}
