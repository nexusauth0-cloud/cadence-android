package com.cadence.core.database

import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [LibraryRepository] for JVM tests and as a spec reference.
 * Not suitable for production — no persistence, no Room.
 */
class InMemoryLibraryRepository : LibraryRepository {

    private val mutex = Mutex()

    private val _saved = MutableStateFlow<List<SavedTrack>>(emptyList())
    private val _history = MutableStateFlow<List<PlayHistoryEntry>>(emptyList())

    override fun observeSaved(): Flow<List<SavedTrack>> = _saved

    override fun observeIsSaved(ref: TrackRef): Flow<Boolean> =
        _saved.map { list -> list.any { it.ref == ref } }

    override suspend fun save(ref: TrackRef, meta: LibraryTrackMeta) {
        mutex.withLock {
            if (_saved.value.any { it.ref == ref }) return
            _saved.update { list ->
                listOf(SavedTrack(ref, System.currentTimeMillis(), meta.title, meta.artist, meta.durationMs, meta.artworkUrl)) + list
            }
        }
    }

    override suspend fun unsave(ref: TrackRef): Boolean =
        mutex.withLock {
            val before = _saved.value.size
            _saved.update { list -> list.filterNot { it.ref == ref } }
            _saved.value.size != before
        }

    override suspend fun savedCount(): Int = _saved.value.size

    override fun observeHistory(): Flow<List<PlayHistoryEntry>> = _history

    override suspend fun recordPlay(ref: TrackRef, meta: LibraryTrackMeta) {
        mutex.withLock {
            _history.update { list ->
                listOf(
                    PlayHistoryEntry(ref, System.currentTimeMillis(), meta.title, meta.artist, meta.durationMs, meta.artworkUrl),
                ) + list
            }
        }
    }

    override suspend fun clearHistory(): Int =
        mutex.withLock {
            val size = _history.value.size
            _history.value = emptyList()
            size
        }

    override suspend fun historyCount(): Int = _history.value.size
}
