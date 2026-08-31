package com.cadence.core.domain

import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.database.QueueStore
import com.cadence.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Observe the durable queue session (ADR-0003). The UI renders now-playing /
 * upcoming / source context from this without ever receiving a raw player
 * snapshot.
 */
class ObserveQueueUseCase(private val store: QueueStore) {
    operator fun invoke(): Flow<QueueSession?> = store.observe()
}

/**
 * Add a track to the end of the queue (add-to-queue). If no session exists,
 * one is created with the added track as the seed.
 */
class AddToQueueUseCase(private val store: QueueStore) {
    suspend operator fun invoke(track: Track) {
        val existing = store.get()
        val tracks = existing?.tracks?.map { it.copy() }?.toMutableList() ?: mutableListOf()
        val currentIndex = existing?.currentIndex ?: 0
        if (tracks.none { it.ref == track.ref }) {
            tracks.add(
                QueueItem(
                    position = tracks.size,
                    ref = track.ref,
                    meta = track.toQueueMeta(),
                    addedReason = QueueAddedReason.USER,
                ),
            )
        }
        store.save(
            (existing ?: QueueSession()).copy(
                kind = existing?.kind ?: QueueKind.MANUAL_LIST,
                currentIndex = currentIndex,
                tracks = tracks.mapIndexed { i, it -> it.copy(position = i) },
            ),
        )
    }
}

/**
 * Insert a track to play next — immediately after the now-playing item. If no
 * session exists, it becomes the seed.
 */
class PlayNextUseCase(private val store: QueueStore) {
    suspend operator fun invoke(track: Track) {
        val existing = store.get()
        if (existing == null || existing.tracks.isEmpty()) {
            store.save(
                QueueSession(
                    kind = QueueKind.MANUAL_LIST,
                    currentIndex = 0,
                    tracks = listOf(
                        QueueItem(0, track.ref, track.toQueueMeta(), QueueAddedReason.SEED),
                    ),
                ),
            )
            return
        }
        val insertAt = existing.currentIndex.let { if (it in existing.tracks.indices) it + 1 else existing.tracks.size }
        val mutable = existing.tracks.toMutableList()
        if (mutable.none { it.ref == track.ref }) {
            mutable.add(insertAt, QueueItem(insertAt, track.ref, track.toQueueMeta(), QueueAddedReason.USER))
        }
        store.save(
            existing.copy(
                tracks = mutable.mapIndexed { i, it -> it.copy(position = i) },
            ),
        )
    }
}

/**
 * Remove an item from the queue at [position]. Returns true if removed.
 */
class RemoveFromQueueUseCase(private val store: QueueStore) {
    suspend operator fun invoke(position: Int): Boolean = store.removeAt(position)
}

/**
 * Transactionally reorder the queue: move [fromPosition] to [toPosition].
 * Current-item consistency and duplicate avoidance are preserved (ADR-0003).
 */
class ReorderQueueUseCase(private val store: QueueStore) {
    suspend operator fun invoke(fromPosition: Int, toPosition: Int): Boolean =
        store.move(fromPosition, toPosition)
}

/** Clear the entire queue session. */
class ClearQueueUseCase(private val store: QueueStore) {
    suspend operator fun invoke() = store.clear()
}

/** Build a queue session for a playlist run (persist intent, not a snapshot). */
class BuildSessionForPlaylistUseCase(private val store: QueueStore) {
    suspend operator fun invoke(tracks: List<Track>, startIndex: Int, contextLabel: String?) {
        if (tracks.isEmpty()) return
        store.save(
            QueueSession(
                kind = QueueKind.PLAYLIST,
                continuationSerialized = contextLabel,
                currentIndex = startIndex.coerceIn(0, tracks.lastIndex),
                tracks = tracks.mapIndexed { i, t ->
                    QueueItem(i, t.ref, t.toQueueMeta(), QueueAddedReason.SEED)
                },
            ),
        )
    }
}

/** Empty store for composition roots/tests that don't need persistence. */
fun emptyQueueStore(): QueueStore = InMemoryQueueStore()

private fun Track.toQueueMeta(): QueueItemMeta =
    QueueItemMeta(
        title = title,
        artist = displayArtist(),
        artworkUrl = artwork?.variants?.maxByOrNull { (it.widthPx ?: 0) * (it.heightPx ?: 0) }?.url,
    )
