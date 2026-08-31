package com.cadence.core.domain

import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.PlayHistoryEntry
import com.cadence.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Record a genuine playback start into history.
 * IMPORTANT: the caller (playback path) invokes this ONLY after a track has
 * successfully resolved AND started playing. Failed or cancelled attempts are
 * never recorded — this is the single choke point that enforces that rule.
 */
class RecordPlayHistoryUseCase(private val library: LibraryRepository) {
    suspend operator fun invoke(track: Track) =
        library.recordPlay(track.ref, track.toLibraryMeta())
}

/** Observe play history (most-recent-first). */
class ObserveHistoryUseCase(private val library: LibraryRepository) {
    operator fun invoke(): Flow<List<PlayHistoryEntry>> = library.observeHistory()
}

/** Clear all play history. Returns number cleared. */
class ClearHistoryUseCase(private val library: LibraryRepository) {
    suspend operator fun invoke(): Int = library.clearHistory()
}
