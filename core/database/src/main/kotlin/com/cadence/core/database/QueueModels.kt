package com.cadence.core.database

import com.cadence.core.model.TrackRef

/**
 * Kind of queue session (ADR-0003). Captures the durable *intent* so restore
 * reconstructs behavior (e.g. endlessness for radio) rather than downgrading to
 * a static list.
 */
enum class QueueKind {
    /** A single track loaded directly — no durable session. */
    MANUAL,
    /** An ordered playlist run; the playlist remains the source of truth. */
    PLAYLIST,
    /** A linear queue of explicitly-added/playing tracks. */
    MANUAL_LIST,
}

/**
 * Why an item entered the queue. Distinguishes user-initiated items from
 * suggested/radio tail so "clear suggested" is a single indexed delete
 * (ADR-0003 rule 6).
 */
enum class QueueAddedReason {
    /** Explicitly added / is the now-playing seed. */
    SEED,
    /** Added by the user (add-to-queue / play-next). */
    USER,
    /** Provider/radio suggestion tail (post-MVP). */
    SUGGESTED,
}

/**
 * Cached display metadata for a queue item (ADR-0003 CACHE COLUMNS). Rendered
 * without re-resolving; never treated as identity.
 */
data class QueueItemMeta(
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
)

/**
 * One item in a [QueueSession]. Carries the track ref plus cached display
 * metadata and how it entered the queue.
 */
data class QueueItem(
    val position: Int,
    val ref: TrackRef,
    val meta: QueueItemMeta = QueueItemMeta(),
    val addedReason: QueueAddedReason = QueueAddedReason.SEED,
)

/**
 * The durable queue session (ADR-0003): a database-backed entity, not a
 * serialized snapshot. `tracks` is the materialized ordered list (shuffle order
 * is persisted as the sequence itself, so restore reproduces exactly what was on
 * screen). The session is a singleton (id=1); a null / empty session means
 * "nothing queued".
 */
data class QueueSession(
    val kind: QueueKind = QueueKind.MANUAL_LIST,
    /** Seed/continuation intent, opaque to the UI (e.g. radio seed). */
    val continuationSerialized: String? = null,
    /** Index of the current/now-playing item within [tracks]. */
    val currentIndex: Int = -1,
    val tracks: List<QueueItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val current: QueueItem? get() = tracks.getOrNull(currentIndex)

    /** Content source label for the queue sheet (e.g. "Playing from My Mix"). */
    val contextLabel: String?
        get() = when (kind) {
            QueueKind.PLAYLIST -> continuationSerialized
            else -> null
        }
}

/**
 * Queue persistence port (ADR-0003). Pure domain — no Room entities, no SQL.
 * The playback engine holds an in-memory mirror for zero-latency ops; the
 * concrete store (Room) is authoritative for restore.
 *
 * Queue item index operations (bulk reorder/remove) MUST use `inBatches()`
 * (ADR-0005) when the caller cannot guarantee <900 IDs.
 */
interface QueueStore {

    /** Observe the current queue session. */
    fun observe(): kotlinx.coroutines.flow.Flow<QueueSession?>

    /** Read the current queue session once (restore path). Null if none. */
    suspend fun get(): QueueSession?

    /**
     * Persist a full queue session. Debounced by the caller (trailing edge);
     * process death loses at most one debounce window (ADR-0003 rule 3).
     */
    suspend fun save(session: QueueSession)

    /** Reposition one item (transactional reorder). */
    suspend fun move(fromPosition: Int, toPosition: Int): Boolean

    /** Remove an item by position. */
    suspend fun removeAt(position: Int): Boolean

    /** Clear the entire queue session (removes session + items). */
    suspend fun clear()
}
