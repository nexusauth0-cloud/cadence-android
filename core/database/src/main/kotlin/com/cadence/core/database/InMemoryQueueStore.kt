package com.cadence.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [QueueStore] for tests and the demo composition root. Mirrors the
 * authoritative Room store contract (ADR-0003): the in-memory session is the
 * zero-latency mirror; a real store persists it.
 */
class InMemoryQueueStore : QueueStore {

    private val _session = MutableStateFlow<QueueSession?>(null)
    override fun observe(): Flow<QueueSession?> = _session

    override suspend fun get(): QueueSession? = _session.value

    override suspend fun save(session: QueueSession) {
        _session.value = session
    }

    override suspend fun move(fromPosition: Int, toPosition: Int): Boolean {
        val s = _session.value ?: return false
        val list = s.tracks
        if (fromPosition !in list.indices || toPosition !in list.indices) return false
        val mutable = list.toMutableList()
        val moved = mutable.removeAt(fromPosition)
        mutable.add(toPosition, moved)
        val newCurrent = when {
            s.currentIndex == fromPosition -> toPosition
            fromPosition < s.currentIndex && toPosition >= s.currentIndex -> s.currentIndex - 1
            fromPosition > s.currentIndex && toPosition <= s.currentIndex -> s.currentIndex + 1
            else -> s.currentIndex
        }
        _session.value = s.copy(
            tracks = mutable.mapIndexed { i, item -> item.copy(position = i) },
            currentIndex = newCurrent,
            updatedAt = System.currentTimeMillis(),
        )
        return true
    }

    override suspend fun removeAt(position: Int): Boolean {
        val s = _session.value ?: return false
        if (position !in s.tracks.indices) return false
        val mutable = s.tracks.toMutableList()
        val removedOnOrBeforeCurrent = position <= s.currentIndex
        mutable.removeAt(position)
        val newCurrent = when {
            s.currentIndex == position -> position.coerceAtMost(mutable.lastIndex)
            removedOnOrBeforeCurrent -> (s.currentIndex - 1).coerceAtLeast(-1)
            else -> s.currentIndex
        }
        _session.value = s.copy(
            tracks = mutable.mapIndexed { i, item -> item.copy(position = i) },
            currentIndex = newCurrent,
            updatedAt = System.currentTimeMillis(),
        )
        return true
    }

    override suspend fun clear() {
        _session.value = null
    }
}
