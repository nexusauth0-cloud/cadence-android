package com.cadence.android.roomdb

import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.QueueDao
import com.cadence.android.roomdb.data.QueueItemEntity
import com.cadence.android.roomdb.data.QueueSessionEntity
import com.cadence.core.database.InMemoryQueueStore
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.database.QueueStore
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Room-backed [QueueStore] (ADR-0003).
 *
 * The live queue lives in an [InMemoryQueueStore] mirror for zero-latency
 * reads/ops (exactly the ADR-0003 contract: "the engine keeps an in-memory
 * mirror for zero-latency ops; Room is authoritative for restore"). Every
 * [save] transactionally persists the full session (session row + item rows) so
 * process death / app restart can reconstruct the exact queue and its intent.
 *
 * Reorder/remove reuse the mirror's position semantics, then persist the result
 * — no divergent branch between memory and disk.
 */
class RoomQueueStore private constructor(
    private val database: CadenceDatabase,
    private val dispatcher: CoroutineDispatcher,
) : QueueStore {

    private val mirror = InMemoryQueueStore()

    companion object {
        fun open(context: android.content.Context): RoomQueueStore =
            RoomQueueStore(CadenceDatabase.create(context), Dispatchers.IO)

        internal fun forTesting(database: CadenceDatabase): RoomQueueStore =
            RoomQueueStore(database, Dispatchers.IO)
    }

    private val dao get() = database.queueDao()

    override fun observe(): Flow<QueueSession?> = mirror.observe()

    override suspend fun get(): QueueSession? {
        val live = mirror.get()
        if (live != null) return live
        // Cold start: hydrate the mirror from Room (authoritative restore).
        val restored = readFromRoom(dao)
        restored?.let { mirror.save(it) }
        return restored
    }

    override suspend fun save(session: QueueSession) {
        mirror.save(session)
        withContext(dispatcher) {
            dao.replaceAll(
                session.toEntity(),
                session.tracks.mapIndexed { i, it -> it.toEntity(i, session.kind) },
            )
        }
    }

    override suspend fun move(fromPosition: Int, toPosition: Int): Boolean {
        val ok = mirror.move(fromPosition, toPosition)
        if (ok) mirror.get()?.let { persist(it) }
        return ok
    }

    override suspend fun removeAt(position: Int): Boolean {
        val ok = mirror.removeAt(position)
        if (ok) {
            mirror.get()?.let { persist(it) } ?: withContext(dispatcher) { dao.deleteItems(); dao.deleteSession() }
        }
        return ok
    }

    override suspend fun clear() {
        mirror.clear()
        withContext(dispatcher) {
            dao.deleteItems()
            dao.deleteSession()
        }
    }

    private suspend fun persist(session: QueueSession) {
        withContext(dispatcher) {
            dao.replaceAll(
                session.toEntity(),
                session.tracks.mapIndexed { i, it -> it.toEntity(i, session.kind) },
            )
        }
    }

    private suspend fun readFromRoom(dao: QueueDao): QueueSession? {
        val entity = dao.getSession() ?: return null
        val items = dao.items()
        if (items.isEmpty()) return null
        return QueueSession(
            kind = runCatching { QueueKind.valueOf(entity.kind) }.getOrDefault(QueueKind.MANUAL_LIST),
            continuationSerialized = entity.continuationSerialized,
            currentIndex = entity.currentIndex.coerceIn(0, items.lastIndex),
            tracks = items.map { it.toDomain() },
        )
    }
}

private fun QueueSession.toEntity(): QueueSessionEntity =
    QueueSessionEntity(
        id = 1,
        kind = kind.name,
        continuationSerialized = continuationSerialized,
        currentIndex = currentIndex,
        updatedAt = updatedAt,
    )

private fun QueueItem.toEntity(position: Int, kind: QueueKind): QueueItemEntity =
    QueueItemEntity(
        sessionId = 1,
        position = position,
        providerId = ref.providerId.value,
        nativeId = ref.nativeId,
        title = meta.title,
        artist = meta.artist,
        artworkUrl = meta.artworkUrl,
        addedReason = addedReason.name,
    )

private fun QueueItemEntity.toDomain(): QueueItem =
    QueueItem(
        position = position,
        ref = TrackRef(ProviderId(providerId), nativeId),
        meta = QueueItemMeta(title = title, artist = artist, artworkUrl = artworkUrl),
        addedReason = runCatching { QueueAddedReason.valueOf(addedReason) }.getOrDefault(QueueAddedReason.SEED),
    )
